package com.xkball.x3dmap.utils;

import com.mojang.logging.LogUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@NonNullByDefault
final class ExpiringResourceCacheScheduler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1, r -> {
        var thread = new Thread(r);
        thread.setDaemon(true);
        thread.setName("x3dmap-cache-cleanup");
        return thread;
    });
    private static final Set<WeakReference<CleanupTarget>> CACHES = ConcurrentHashMap.newKeySet();

    static {
        SCHEDULER.scheduleAtFixedRate(ExpiringResourceCacheScheduler::runCleanUp, 5, 5, TimeUnit.SECONDS);
    }

    private ExpiringResourceCacheScheduler() {
    }

    static void register(CleanupTarget cache) {
        CACHES.add(new WeakReference<>(cache));
    }

    private static void runCleanUp() {
        var iter = CACHES.iterator();
        while (iter.hasNext()) {
            var cache = iter.next().get();
            if (cache == null) {
                iter.remove();
                continue;
            }
            try {
                if (!cache.cleanupExpired()) {
                    iter.remove();
                }
            } catch (Exception e) {
                LOGGER.error("Failed to clean up resource cache", e);
            }
        }
    }

    interface CleanupTarget {
        boolean cleanupExpired();
    }
}
