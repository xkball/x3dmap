package com.xkball.x3dmap.server;

import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class ChunkRequest implements BiConsumer<ChunkPos, ChunkResult<ChunkAccess>> {
    final ArrayList<ChunkPos> poses;
    final Ticket ticket;
    final ChunkStatus status;

    protected final ServerPlayer player;
    protected final ServerLevel level;

    int position = 0;
    int completion = 0;

    public ChunkRequest(List<ChunkPos> poses, TicketType type, ChunkStatus status, ServerPlayer player, ServerLevel level) {
        this.poses = new ArrayList<>(poses);
        this.status = status;
        this.ticket = new Ticket(type, ChunkLevel.byStatus(status));
        this.player = player;
        this.level = level;

        this.poses.sort(BY_REGION_AND_BY_POS);
    }

    public Consumer<ChunkResult<ChunkAccess>> curry(ChunkPos pos) {
        return res -> accept(pos, res);
    }

    private static int subRegionX(ChunkPos pos) {
        return pos.x() >> 3;
    }

    private static int subRegionZ(ChunkPos pos) {
        return pos.z() >> 3;
    }

    private static final Comparator<ChunkPos> BY_REGION_AND_BY_POS = Comparator
            .<ChunkPos>comparingInt(ChunkPos::getRegionX)
            .thenComparingInt(ChunkPos::getRegionZ)
            .thenComparingInt(ChunkRequest::subRegionX)
            .thenComparingInt(ChunkRequest::subRegionZ)
            .thenComparingInt(ChunkPos::x)
            .thenComparingInt(ChunkPos::z);
}
