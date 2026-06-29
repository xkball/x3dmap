package com.xkball.x3dmap.utils;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DualQueueThreadPool {
    
    private final BlockingQueue<Runnable> mainQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Runnable> workerQueue = new LinkedBlockingQueue<>();
    
    private final int workerCount;
    public final ExecutorService workers;
    
    public DualQueueThreadPool() {
        this(8);
    }
    
    public DualQueueThreadPool(int workerCount) {
        this.workerCount = workerCount;
        this.workers = Executors.newFixedThreadPool(workerCount);
    }
    
    public void clear() {
        this.mainQueue.clear();
        this.workerQueue.clear();
    }
    
    public void submitMain(Runnable task) {
        mainQueue.offer(task);
    }
    
    public void submitWorker(Runnable task) {
        workerQueue.offer(task);
    }
    
    public void submitAsyncIgnoreMain(Runnable task) {
        workers.submit(task);
    }
    
    public int taskCount() {
        return mainQueue.size() + workerQueue.size();
    }
    
    public void runFor10ms() {
        if (this.mainQueue.isEmpty() && this.workerQueue.isEmpty()) return;
        long startTime = System.nanoTime();
        long duration = TimeUnit.MILLISECONDS.toNanos(10);
        
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch latch = new CountDownLatch(workerCount);
        
        for (int i = 0; i < workerCount; i++) {
            workers.submit(() -> {
                try {
                    while (running.get()) {
                        if (System.nanoTime() - startTime > duration) {
                            break;
                        }
                        Runnable task = workerQueue.poll();
                        if (task != null) {
                            task.run();
                        } else {
                            break;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        while (true) {
            if (System.nanoTime() - startTime > duration) {
                break;
            }
            Runnable task = mainQueue.poll();
            if (task == null) {
                task = workerQueue.poll();
            }
            if (task != null) {
                task.run();
            } else {
                break;
            }
        }
        
        running.set(false);
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public void shutdown() {
        workers.shutdown();
    }
}