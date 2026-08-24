package com.xkball.x3dmap.client.terrain.render;

import com.mojang.blaze3d.vertex.TlsfAllocator;
import com.mojang.blaze3d.vertex.UberGpuBuffer;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.utils.ClientUtils;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

@NonNullByDefault
public final class GpuNodeModel implements AutoCloseable {

    private final UberGpuBuffer<Long> buffer;
    private final long key;
    private final TlsfAllocator.@Nullable Allocation allocation;
    private final int offset;
    private final int len;
    private final AtomicLong revision = new AtomicLong(0);
    private volatile boolean dirty;
    private volatile boolean refreshing;

    public GpuNodeModel(UberGpuBuffer<Long> buffer, long key, TlsfAllocator.@Nullable Allocation allocation, int offset, int len) {
        this.buffer = buffer;
        this.key = key;
        this.allocation = allocation;
        this.offset = offset;
        this.len = len;
    }

    public UberGpuBuffer<Long> buffer() {
        return this.buffer;
    }

    public long key() {
        return this.key;
    }

    public TlsfAllocator.@Nullable Allocation allocation() {
        return this.allocation;
    }

    public int offset() {
        return this.offset;
    }

    public int len() {
        return this.len;
    }

    public void invalidate() {
        this.revision.incrementAndGet();
        this.dirty = true;
    }

    public long beginRefresh() {
        if (!this.dirty || this.refreshing) return -1;
        this.refreshing = true;
        return this.revision.get();
    }

    public boolean isRevisionCurrent(long revision) {
        return this.revision.get() == revision;
    }

    public void finishRefresh() {
        this.refreshing = false;
    }

    @Override
    public void close() {
        if (this.allocation != null && this.buffer.getAllocation(this.key) == this.allocation) {
            this.buffer.removeAllocation(this.key);
            this.buffer.uploadStagedAllocations(ClientUtils.getGpuDevice(), ClientUtils.getCommandEncoder());
        }
    }
}
