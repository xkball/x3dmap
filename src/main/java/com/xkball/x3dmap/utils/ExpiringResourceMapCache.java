package com.xkball.x3dmap.utils;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.X3dMapClient;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Function;

@NonNullByDefault
public class ExpiringResourceMapCache<K, V> implements AutoCloseable, ExpiringResourceCacheScheduler.CleanupTarget {

    private static final Logger LOGGER = LogUtils.getLogger();
    
    private final @Nullable Executor loaderExecutor;
    private final @Nullable Executor unloaderExecutor;
    private final @Nullable Function<K, ? extends V> loader;
    private final @Nullable Function<K, ? extends CompletableFuture<? extends V>> asyncLoader;
    private final long expireNano;
    private final Map<K, Entry<V>> cache = new ConcurrentHashMap<>();
    private final Map<K, PendingLoad<V>> loading = new ConcurrentHashMap<>();
    private volatile boolean closed = false;

    private ExpiringResourceMapCache(@Nullable Executor loaderExecutor, @Nullable Executor unloaderExecutor,
                                  @Nullable Function<K, ? extends V> loader,
                                  @Nullable Function<K, ? extends CompletableFuture<? extends V>> asyncLoader,
                                  long expireS) {
        this.loaderExecutor = loaderExecutor;
        this.unloaderExecutor = unloaderExecutor;
        this.loader = loader;
        this.asyncLoader = asyncLoader;
        this.expireNano = TimeUnit.SECONDS.toNanos(expireS);
        if (this.expireNano > 0) ExpiringResourceCacheScheduler.register(this);
    }
    
    public static <K,V> Builder<K,V> builder(){
        return new Builder<>();
    }
    
    @Override
    public boolean cleanupExpired() {
        if (this.closed) return false;
        var iter = this.cache.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            if (entry.getValue().isExpire(this.expireNano)) {
                iter.remove();
                this.closeValue(entry.getValue().value);
            }
        }
        return true;
    }
    
    public @Nullable V get(K key) {
        var entry = this.cache.get(key);
        if (entry != null && entry.require()) {
            return entry.value;
        }
        return null;
    }
    
    public boolean loading(K key){
        return loading.containsKey(key);
    }
    
    public @Nullable V getOrCreateAsync(K key) {
        return this.getAsync(key).getNow(null);
    }

    public void remove(K key) {
        this.cancelLoad(key);
        var entry = this.cache.remove(key);
        if (entry == null) return;
        this.closeValue(entry.value);
    }

    public void cancelLoad(K key) {
        var pendingLoad = this.loading.remove(key);
        if (pendingLoad != null) pendingLoad.cancel();
    }

    public void replace(K key, V value) {
        this.cancelLoad(key);
        if (this.closed) {
            this.closeValue(value);
            return;
        }
        var oldEntry = this.cache.put(key, new Entry<>(value));
        if (oldEntry != null) this.closeValue(oldEntry.value);
    }

    public List<V> values() {
        var result = new ArrayList<V>(this.cache.size());
        for (var entry : this.cache.values()) {
            result.add(entry.value);
        }
        return result;
    }

    public int size() {
        return this.cache.size();
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
        if (list.isEmpty()) return CompletableFuture.completedFuture(List.of());
        var result = new CompletableFuture<List<V>>();
        var size = list.size();
        var values = new Object[size];
        var remaining = new AtomicInteger(size);
        for (var i = 0; i < size; i++) {
            var index = i;
            this.getAsync(list.get(i)).whenComplete((value, error) -> {
                if (error != null) {
                    result.completeExceptionally(error);
                    return;
                }
                values[index] = value;
                if (remaining.decrementAndGet() == 0) {
                    result.complete(new ArrayBackedList<>(values));
                }
            });
        }
        return result;
    }
    
    private static final class ArrayBackedList<V> extends AbstractList<V> implements RandomAccess {
        
        private final Object[] values;
        
        private ArrayBackedList(Object[] values) {
            this.values = values;
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public V get(int index) {
            return (V) this.values[index];
        }
        
        @Override
        public int size() {
            return this.values.length;
        }
    }
    
    private PendingLoad<V> load(K key) {
        var pendingLoad = new PendingLoad<V>();
        CompletableFuture<? extends V> source;
        if (this.asyncLoader != null) {
            source = this.asyncLoader.apply(key);
        } else {
            assert this.loader != null;
            var executor = this.loaderExecutor == null ? X3dMapClient.taskExecutor : this.loaderExecutor;
            source = CompletableFuture.supplyAsync(() -> this.loader.apply(key), executor);
        }
        source.whenCompleteAsync((loadedValue, error) -> this.finishLoad(key, pendingLoad, loadedValue, error), X3dMapClient.taskExecutor);
        pendingLoad.source = source;
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
        if (pendingLoad.accepted && !pendingLoad.cancelled) {
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
        private boolean cancelled;

        private void cancel() {
            this.future.cancel(false);
            this.cancelled = true;
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
        
        public ExpiringResourceMapCache<K, V> build() {
            if (this.loader == null && this.asyncLoader == null) {
                throw new IllegalStateException("Cache loader can not be null.");
            }
            if (this.loader != null && this.asyncLoader != null) {
                throw new IllegalStateException("Only one cache loader can be configured.");
            }
            return new ExpiringResourceMapCache<>(loaderExecutor, unloaderExecutor, loader, asyncLoader, expireS);
        }
        
    }
}
