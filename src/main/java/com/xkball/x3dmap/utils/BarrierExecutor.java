package com.xkball.x3dmap.utils;

import com.xkball.xklibmc.annotation.NonNullByDefault;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@NonNullByDefault
public final class BarrierExecutor extends AbstractExecutorService {

    private final ExecutorService executor;
    private final Object generationLock = new Object();
    private Generation generation = new Generation(CompletableFuture.completedFuture(null));

    public BarrierExecutor(int size) {
        this.executor = Executors.newFixedThreadPool(size);
    }

    public BarrierExecutor(int size, ThreadFactory threadFactory) {
        this.executor = Executors.newFixedThreadPool(size, threadFactory);
    }

    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command);
        synchronized (this.generationLock) {
            var current = this.generation;
            current.pending++;
            try {
                this.executor.execute(() -> this.run(current, command));
            } catch (RuntimeException | Error exception) {
                current.pending--;
                throw exception;
            }
        }
    }

    public CompletableFuture<Void> submitBarrier() {
        CompletableFuture<Void> barrier;
        boolean complete;
        synchronized (this.generationLock) {
            var previous = this.generation;
            this.generation = new Generation(previous.completion);
            barrier = previous.completion;
            complete = previous.pending == 0;
        }
        if (complete) {
            barrier.complete(null);
        }
        return barrier;
    }

    @Override
    public void shutdown() {
        this.executor.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return this.executor.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return this.executor.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return this.executor.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return this.executor.awaitTermination(timeout, unit);
    }

    private void run(Generation current, Runnable command) {
        current.gate.join();
        try {
            command.run();
        } finally {
            this.finish(current);
        }
    }

    private void finish(Generation current) {
        boolean complete;
        synchronized (this.generationLock) {
            current.pending--;
            complete = current.pending == 0;
        }
        if (complete) {
            current.completion.complete(null);
        }
    }

    private static final class Generation {

        private final CompletableFuture<Void> gate;
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private int pending;

        private Generation(CompletableFuture<Void> gate) {
            this.gate = gate;
        }
    }
}
