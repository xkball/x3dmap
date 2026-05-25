package com.xkball.x3dmap.server;

import com.google.common.base.Preconditions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;

import java.util.ConcurrentModificationException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class ChunkBatcher implements Runnable {
    private static final int MAX_PARALLEL_TASKS = 64;
    private static final Executor BATCHER_EXECUTOR = Executors.newSingleThreadExecutor(it -> new Thread(it, "ChunkBatcher"));
    
    private static ChunkBatcher HANDLE;
    
    public static void init(MinecraftServer server) {
        Preconditions.checkState(HANDLE == null);
        HANDLE = new ChunkBatcher(server);
    }
    
    public static void deInit() {
        Preconditions.checkState(HANDLE != null);
        HANDLE = null;
    }
    
    public static void scheduleIfNeeded(MinecraftServer server) {
        Preconditions.checkState(HANDLE != null);
        
        if (HANDLE.needSchedule) {
            server.executeIfPossible(HANDLE);
        }
    }
    
    public static void submitRequest(ChunkRequest request) {
        Preconditions.checkState(HANDLE != null);
        HANDLE.submit(request);
    }
    
    private final Queue<ChunkRequest> pendingTasks = new LinkedList<>();
    private final MinecraftServer server;
    
    private boolean needSchedule = false;
    private int activeCount = 0;
    
    public ChunkBatcher(MinecraftServer server) {
        this.server = server;
    }
    
    private void submit(ChunkRequest request) {
        if (!server.isSameThread()) {
            throw new ConcurrentModificationException("ChunkRequest is submitted off the thread");
        }
        pendingTasks.add(request);
        needSchedule = true;
    }
    
    @Override
    public void run() {
        if (!server.isSameThread()) {
            throw new ConcurrentModificationException("ChunkBatcher is ran off the thread");
        }
        needSchedule = false;
        
        ChunkRequest request = pendingTasks.peek();
        if (request == null) {
            return; // Should not happen
        }
        ServerChunkCache chunkSource = request.level.getChunkSource();
        int i = activeCount;
        int j = request.position;
        var ticket = request.ticket;
        var poses = request.poses;
        for (; i < MAX_PARALLEL_TASKS && j < poses.size(); i++, j++) {
            chunkSource.addTicket(ticket, poses.get(j));
        }
        if (i == activeCount) {
            return;
        }
        chunkSource.runDistanceManagerUpdates();
        for (int k = request.position; k < j; k++) {
            var pos = poses.get(k);
            int x = pos.x(), z = pos.z();
            var status = request.status;
            CompletableFuture.supplyAsync(() -> chunkSource.getChunkFuture(x, z, status, true), BATCHER_EXECUTOR)
                    .thenCompose(Function.identity())
                    .thenAcceptAsync(request.curry(pos), server)
                    .whenCompleteAsync((_, _) -> {
                        chunkSource.ticketStorage.removeTicket(ticket, pos);
                        onCompletion();
                    }, server);
        }
        this.activeCount = i;
        request.position = j;
    }
    
    public void onCompletion() {
        activeCount--;
        needSchedule = true;
        final var request = pendingTasks.peek();
        Preconditions.checkState(request != null);
        if (++request.completion == request.poses.size()) {
            pendingTasks.poll();
        }
    }
}
