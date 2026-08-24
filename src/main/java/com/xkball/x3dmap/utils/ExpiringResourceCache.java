package com.xkball.x3dmap.utils;

import com.mojang.logging.LogUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Function;

@NonNullByDefault
public class ExpiringResourceCache<K, V> implements AutoCloseable {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1, r -> {
        var thread = new Thread(r);
        thread.setDaemon(true);
        thread.setName("x3dmap-cache-cleanup");
        return thread;
    });
    private static final Set<WeakReference<ExpiringResourceCache<?, ?>>> CACHES = ConcurrentHashMap.newKeySet();
    
    static {
        SCHEDULER.scheduleAtFixedRate(ExpiringResourceCache::runCleanUp, 5, 5, TimeUnit.SECONDS);
    }
    
    private final @Nullable Executor loaderExecutor;
    private final @Nullable Executor unloaderExecutor;
    private final @Nullable Function<K, ? extends V> loader;
    private final @Nullable Function<K, ? extends CompletableFuture<? extends V>> asyncLoader;
    private final long expireNano;
    private final Map<K, Entry<V>> cache = new ConcurrentHashMap<>();
    private final Map<K, PendingLoad<V>> loading = new ConcurrentHashMap<>();
    private volatile boolean closed = false;

    private ExpiringResourceCache(@Nullable Executor loaderExecutor, @Nullable Executor unloaderExecutor,
                                  @Nullable Function<K, ? extends V> loader,
                                  @Nullable Function<K, ? extends CompletableFuture<? extends V>> asyncLoader,
                                  long expireS) {
        this.loaderExecutor = loaderExecutor;
        this.unloaderExecutor = unloaderExecutor;
        this.loader = loader;
        this.asyncLoader = asyncLoader;
        this.expireNano = TimeUnit.SECONDS.toNanos(expireS);
        if (this.expireNano > 0) CACHES.add(new WeakReference<>(this));
    }
    
    public static <K,V> Builder<K,V> builder(){
        return new Builder<>();
    }
    
    private static void runCleanUp() {
        var iter_ = CACHES.iterator();
        while (iter_.hasNext()) {
            var ref = iter_.next();
            var cache = ref.get();
            if (cache == null || cache.closed) {
                iter_.remove();
                continue;
            }
            var iter = cache.cache.entrySet().iterator();
            while (iter.hasNext()) {
                var entry = iter.next();
                if (entry.getValue().isExpire(cache.expireNano)) {
                    iter.remove();
                    cache.closeValue(entry.getValue().value);
                }
            }
        }
    }
    
    public @Nullable V get(K key) {
        var entry = this.cache.get(key);
        if (entry != null && entry.require()) {
            return entry.value;
        }
        return null;
    }
    
    public @Nullable V getOrCreateAsync(K key) {
        return this.getAsync(key).getNow(null);
    }

    public void remove(K key) {
        var pendingLoad = this.loading.remove(key);
        if (pendingLoad != null) pendingLoad.cancel();
        var entry = this.cache.remove(key);
        if (entry == null) return;
        this.closeValue(entry.value);
    }

    public List<V> values() {
        var result = new ArrayList<V>(this.cache.size());
        for (var entry : this.cache.values()) {
            result.add(entry.value);
        }
        return result;
    }

    private void closeValue(Object value) {
        if (!(value instanceof AutoCloseable closeable)) return;
        if (this.unloaderExecutor == null) {
            closeResource(closeable);
            return;
        }
        try {
            this.unloaderExecutor.execute(() -> closeResource(closeable));
        } catch (Exception e) {
            LOGGER.error("Failed to close {}", closeable, e);
        }
    }

    private static void closeResource(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            LOGGER.error("Failed to close {}", closeable, e);
        }
    }
    
    public V getBlocked(K key) {
        return this.getAsync(key).join();
    }
    
    public CompletableFuture<V> getAsync(K key) {
        if (this.closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Cache is closed"));
        }
        var r = this.get(key);
        if (r != null) return CompletableFuture.completedFuture(r);
        return this.loading.computeIfAbsent(key, this::load).future;
    }
    
    public CompletableFuture<List<V>> getListAsync(List<K> list){
        var size = list.size();
        var futures = new ArrayList<CompletableFuture<V>>(size);
        for (var key : list) {
            futures.add(this.getAsync(key));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApplyAsync((_) -> {
                    var result = new ArrayList<V>(size);
                    for(var future : futures){
                        result.add(future.join());
                    }
                    return result;
                });
    }
    
    private PendingLoad<V> load(K key) {
        var value = this.get(key);
        var pendingLoad = new PendingLoad<V>();
        var executor = this.loaderExecutor == null ? ForkJoinPool.commonPool() : this.loaderExecutor;
        CompletableFuture<? extends V> source;
        if (value != null) {
            source = CompletableFuture.completedFuture(value);
        } else if (this.asyncLoader != null) {
            try {
                source = this.asyncLoader.apply(key);
            } catch (RuntimeException e) {
                source = CompletableFuture.failedFuture(e);
            }
        } else {
            assert this.loader != null;
            source = CompletableFuture.supplyAsync(() -> this.loader.apply(key), executor);
        }
        pendingLoad.source = source;
        source.whenCompleteAsync((loadedValue, error) -> this.finishLoad(key, pendingLoad, loadedValue, error), executor);
        return pendingLoad;
    }

    private void finishLoad(K key, PendingLoad<V> pendingLoad, V value, @Nullable Throwable error) {
        if (error != null) {
            this.loading.remove(key, pendingLoad);
            pendingLoad.future.completeExceptionally(error);
            return;
        }
        this.loading.compute(key, (_, current) -> {
            if (current != pendingLoad || this.closed) return current;
            this.put(key, value);
            pendingLoad.accepted = true;
            return null;
        });
        if (pendingLoad.accepted) {
            pendingLoad.future.complete(value);
        } else {
            this.closeValue(value);
            pendingLoad.future.cancel(false);
        }
    }
    
    private void put(K key, V value) {
        this.cache.put(key, new Entry<>(value));
    }
    
    @Override
    public void close() {
        this.closed = true;
        for (var pendingLoad : this.loading.values()) {
            pendingLoad.cancel();
        }
        this.loading.clear();
        for(var c : this.cache.values()){
            this.closeValue(c.value);
        }
        this.cache.clear();
    }

    private static class PendingLoad<V> {
        private final CompletableFuture<V> future = new CompletableFuture<>();
        private @Nullable CompletableFuture<? extends V> source;
        private boolean accepted;

        private void cancel() {
            this.future.cancel(false);
            if (this.source != null) this.source.cancel(false);
        }
    }

    private static class Entry<V> {
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<Entry> CAS_HELPER = AtomicIntegerFieldUpdater.newUpdater(Entry.class, "read");
        public final V value;
        private volatile long time;
        private volatile int read = 0;
        
        public Entry(V value) {
            this.value = value;
            this.time = System.nanoTime();
        }
        
        public boolean require(){
            while (true){
                if (read == -1) return false;
                else if(CAS_HELPER.compareAndSet(this, read, read+1)) break;
            }
            this.time = System.nanoTime();
            CAS_HELPER.decrementAndGet(this);
            return true;
        }
        
        public boolean isExpire(long expire) {
            if (System.nanoTime() - this.time <= expire) {
                return false;
            }
            return CAS_HELPER.compareAndSet(this,0, -1);
        }
    }
    
    public static class Builder<K, V> {
        
        private @Nullable Executor loaderExecutor;
        private @Nullable Executor unloaderExecutor;
        private @Nullable Function<K, ? extends V> loader;
        private @Nullable Function<K, ? extends CompletableFuture<? extends V>> asyncLoader;
        private long expireS = -1;
        
        public Builder() {
        
        }
        
        public Builder<K, V> loader(Function<K, ? extends V> loader) {
            this.loader = loader;
            return this;
        }

        public Builder<K, V> asyncLoader(Function<K, ? extends CompletableFuture<? extends V>> loader) {
            this.asyncLoader = loader;
            return this;
        }
        
        public Builder<K, V> loadOn(Executor executor) {
            this.loaderExecutor = executor;
            return this;
        }
        
        public Builder<K, V> unloadOn(Executor executor) {
            this.unloaderExecutor = executor;
            return this;
        }
        
        public Builder<K, V> expireAfterRead(long duration) {
            this.expireS = duration;
            return this;
        }
        
        public ExpiringResourceCache<K, V> build() {
            if (this.loader == null && this.asyncLoader == null) {
                throw new IllegalStateException("Cache loader can not be null.");
            }
            if (this.loader != null && this.asyncLoader != null) {
                throw new IllegalStateException("Only one cache loader can be configured.");
            }
            return new ExpiringResourceCache<>(loaderExecutor, unloaderExecutor, loader, asyncLoader, expireS);
        }
        
    }
}
