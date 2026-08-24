package com.xkball.x3dmap.utils;

import com.mojang.logging.LogUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.slf4j.Logger;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@NonNullByDefault
public final class TimeBudgetExecutor implements Executor {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    @Override
    public void execute(Runnable command) {
        this.tasks.offer(command);
    }

    public void runFor(long duration, TimeUnit timeUnit) {
        var deadline = System.nanoTime() + timeUnit.toNanos(duration);
        while (System.nanoTime() < deadline) {
            var task = this.tasks.poll();
            if (task == null) return;
            try {
                task.run();
            } catch (RuntimeException e) {
                LOGGER.error("Failed to execute time-budgeted task", e);
            }
        }
    }
}
