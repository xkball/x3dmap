package com.xkball.x3dmap.utils;

import com.xkball.xklibmc.annotation.NonNullByDefault;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

@NonNullByDefault
public final class MonitoredExecutor implements Executor, AutoCloseable{

    private static final long THROUGHPUT_SAMPLE_NANOS = TimeUnit.SECONDS.toNanos(1);

    private final Executor executor;
    private final LongAdder waitingTaskCount = new LongAdder();
    private final LongAdder completedTaskCount = new LongAdder();
    private final AtomicReference<ThroughputSample> throughputSample = new AtomicReference<>(
            new ThroughputSample(System.nanoTime(), 0, 0));

    public MonitoredExecutor(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void execute(Runnable command) {
        TrackedTask trackedTask = new TrackedTask(this, command);
        boolean accepted = false;
        this.waitingTaskCount.increment();
        try {
            this.executor.execute(trackedTask);
            accepted = true;
        } finally {
            if (!accepted && !trackedTask.isStarted()) {
                this.waitingTaskCount.decrement();
            }
        }
    }

    public long getWaitingTaskCount() {
        return this.waitingTaskCount.sum();
    }

    public double getThroughputPerSecond() {
        while (true) {
            ThroughputSample previousSample = this.throughputSample.get();
            long now = System.nanoTime();
            long elapsedNanos = now - previousSample.timeNanos();
            if (elapsedNanos < THROUGHPUT_SAMPLE_NANOS) {
                return previousSample.tasksPerSecond();
            }
            long completedTasks = this.completedTaskCount.sum();
            double tasksPerSecond = (completedTasks - previousSample.completedTaskCount())
                    * (double) THROUGHPUT_SAMPLE_NANOS / elapsedNanos;
            ThroughputSample currentSample = new ThroughputSample(now, completedTasks, tasksPerSecond);
            if (this.throughputSample.compareAndSet(previousSample, currentSample)) {
                return tasksPerSecond;
            }
        }
    }
    
    @Override
    public void close() throws Exception {
        if(this.executor instanceof ExecutorService es){
            es.close();
        }
    }
    
    private record ThroughputSample(long timeNanos, long completedTaskCount, double tasksPerSecond) {
    }

    private static final class TrackedTask implements Runnable {

        private final MonitoredExecutor executor;
        private final Runnable command;
        private boolean started;

        private TrackedTask(MonitoredExecutor executor, Runnable command) {
            this.executor = executor;
            this.command = command;
        }

        @Override
        public void run() {
            this.started = true;
            this.executor.waitingTaskCount.decrement();
            try {
                this.command.run();
            } finally {
                this.executor.completedTaskCount.increment();
            }
        }

        private boolean isStarted() {
            return this.started;
        }
    }
}
