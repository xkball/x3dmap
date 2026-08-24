package com.xkball.x3dmap.utils;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.X3dMapClient;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

@NonNullByDefault
public class ExpiringResourceCache<T> implements AutoCloseable, ExpiringResourceCacheScheduler.CleanupTarget {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final @Nullable Executor loaderExecutor;
    private final @Nullable Executor unloaderExecutor;
    private final @Nullable Supplier<? extends T> loader;
    private final @Nullable Supplier<? extends CompletableFuture<? extends T>> asyncLoader;
    private final @Nullable Consumer<? super T> unloader;
    private final long expireNano;
    private final AtomicReference<@Nullable Entry<T>> cache = new AtomicReference<>();
    private final AtomicReference<@Nullable PendingLoad<T>> loading = new AtomicReference<>();

    private ExpiringResourceCache(@Nullable Executor loaderExecutor, @Nullable Executor unloaderExecutor,
                                  @Nullable Supplier<? extends T> loader,
                                  @Nullable Supplier<? extends CompletableFuture<? extends T>> asyncLoader,
                                  @Nullable Consumer<? super T> unloader, long expireS) {
        this.loaderExecutor = loaderExecutor;
        this.unloaderExecutor = unloaderExecutor;
        this.loader = loader;
        this.asyncLoader = asyncLoader;
        this.unloader = unloader;
        this.expireNano = TimeUnit.SECONDS.toNanos(expireS);
        if (this.expireNano > 0) ExpiringResourceCacheScheduler.register(this);
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public @Nullable T get() {
        var entry = this.cache.get();
        if (entry != null && entry.require()) {
            return entry.value;
        }
        return null;
    }

    public boolean loading() {
        return this.loading.get() != null;
    }

    public @Nullable T getOrCreateAsync() {
        return this.getAsync().getNow(null);
    }

    public T getBlocked() {
        return this.getAsync().join();
    }

    public CompletableFuture<T> getAsync() {
        var value = this.get();
        if (value != null) return CompletableFuture.completedFuture(value);
        PendingLoad<T> pending;
        synchronized (this) {
            value = this.get();
            if (value != null) return CompletableFuture.completedFuture(value);
            var current = this.loading.get();
            if (current != null) return current.future;
            pending = new PendingLoad<>();
            this.loading.set(pending);
            this.startLoad(pending);
            return pending.future;
        }
    }

    public synchronized void replace(T value) {
        var pending = this.loading.getAndSet(null);
        var oldEntry = this.cache.getAndSet(new Entry<>(value));
        if (pending != null) pending.cancel();
        if (oldEntry != null) this.unloadValue(oldEntry.value);
    }

    public synchronized void remove() {
        var pending = this.loading.getAndSet(null);
        var oldEntry = this.cache.getAndSet(null);
        if (pending != null) pending.cancel();
        if (oldEntry != null) this.unloadValue(oldEntry.value);
    }

    @Override
    public boolean cleanupExpired() {
        var entry = this.cache.get();
        if (entry != null && entry.isExpire(this.expireNano) && this.cache.compareAndSet(entry, null)) {
            this.unloadValue(entry.value);
        }
        return true;
    }

    private void startLoad(PendingLoad<T> pending) {
        CompletableFuture<? extends T> source;
        if (this.asyncLoader != null) {
            source = this.asyncLoader.get();
        } else {
            assert this.loader != null;
            source = CompletableFuture.supplyAsync(this.loader, this.loaderExecutor == null ? X3dMapClient.taskExecutor : this.loaderExecutor);
        }
        pending.source = source;
        source.whenCompleteAsync((value, error) -> this.finishLoad(pending, value, error), X3dMapClient.taskExecutor);
    }

    private void finishLoad(PendingLoad<T> pending, @Nullable T value, @Nullable Throwable error) {
        if (error != null) {
            this.loading.compareAndSet(pending, null);
            pending.future.completeExceptionally(error);
            return;
        }
        Entry<T> oldEntry = null;
        boolean accepted = !pending.cancelled;
        synchronized (this) {
            accepted &= this.loading.compareAndSet(pending, null);
            if (accepted) oldEntry = this.cache.getAndSet(new Entry<>(value));
        }
        if (accepted) {
            if (oldEntry != null) this.unloadValue(oldEntry.value);
            pending.future.complete(value);
        } else {
            this.unloadValue(value);
            pending.future.cancel(false);
        }
    }

    private void unloadValue(@Nullable T value) {
        if (value == null) return;
        Runnable action = () -> {
            if (this.unloader != null) {
                try {
                    this.unloader.accept(value);
                } catch (Exception e) {
                    LOGGER.error("Failed to unload {}", value, e);
                }
            } else if (value instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    LOGGER.error("Failed to close {}", closeable, e);
                }
            }
        };
        if (this.unloaderExecutor == null) {
            action.run();
            return;
        }
        try {
            this.unloaderExecutor.execute(action);
        } catch (Exception e) {
            LOGGER.error("Failed to schedule unload for {}", value, e);
        }
    }

    @Override
    public void close() {
        PendingLoad<T> pending;
        Entry<T> entry;
        synchronized (this) {
            pending = this.loading.getAndSet(null);
            entry = this.cache.getAndSet(null);
        }
        if (pending != null) pending.cancel();
        if (entry != null) this.unloadValue(entry.value);
    }

    private static final class PendingLoad<T> {
        private final CompletableFuture<T> future = new CompletableFuture<>();
        private volatile @Nullable CompletableFuture<? extends T> source;
        private volatile boolean cancelled;

        private void cancel() {
            this.future.cancel(false);
            this.cancelled = true;
        }
    }

    private static final class Entry<T> {
        private final T value;
        private volatile long time;
        private volatile int read;

        private Entry(T value) {
            this.value = value;
            this.time = System.nanoTime();
        }

        private synchronized boolean require() {
            if (this.read < 0) return false;
            this.read++;
            this.time = System.nanoTime();
            this.read--;
            return true;
        }

        private synchronized boolean isExpire(long expire) {
            if (System.nanoTime() - this.time <= expire) return false;
            if (this.read != 0) return false;
            this.read = -1;
            return true;
        }
    }

    public static class Builder<T> {

        private @Nullable Executor loaderExecutor;
        private @Nullable Executor unloaderExecutor;
        private @Nullable Supplier<? extends T> loader;
        private @Nullable Supplier<? extends CompletableFuture<? extends T>> asyncLoader;
        private @Nullable Consumer<? super T> unloader;
        private long expireS = -1;

        public Builder<T> loader(Supplier<? extends T> loader) {
            this.loader = loader;
            return this;
        }

        public Builder<T> asyncLoader(Supplier<? extends CompletableFuture<? extends T>> loader) {
            this.asyncLoader = loader;
            return this;
        }

        public Builder<T> unloader(Consumer<? super T> unloader) {
            this.unloader = unloader;
            return this;
        }

        public Builder<T> unload(Consumer<? super T> unloader) {
            return this.unloader(unloader);
        }

        public Builder<T> loadOn(Executor executor) {
            this.loaderExecutor = executor;
            return this;
        }

        public Builder<T> unloadOn(Executor executor) {
            this.unloaderExecutor = executor;
            return this;
        }

        public Builder<T> expireAfterRead(long duration) {
            this.expireS = duration;
            return this;
        }

        public ExpiringResourceCache<T> build() {
            if (this.loader == null && this.asyncLoader == null) {
                throw new IllegalStateException("Cache loader can not be null.");
            }
            if (this.loader != null && this.asyncLoader != null) {
                throw new IllegalStateException("Only one cache loader can be configured.");
            }
            return new ExpiringResourceCache<>(this.loaderExecutor, this.unloaderExecutor,
                    this.loader, this.asyncLoader, this.unloader, this.expireS);
        }
    }
}
