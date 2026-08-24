package com.xkball.x3dmap.client.terrain.render;

import com.mojang.blaze3d.vertex.TlsfAllocator;
import com.mojang.blaze3d.vertex.UberGpuBuffer;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.utils.ClientUtils;
import org.jspecify.annotations.Nullable;

@NonNullByDefault
public record GpuNodeModel(UberGpuBuffer<Long> buffer, long key, TlsfAllocator.@Nullable Allocation allocation,
                           int offset, int len) implements AutoCloseable {

    @Override
    public void close() {
        if (this.allocation != null && this.buffer.getAllocation(this.key) == this.allocation) {
            this.buffer.removeAllocation(this.key);
            this.buffer.uploadStagedAllocations(ClientUtils.getGpuDevice(), ClientUtils.getCommandEncoder());
        }
    }
}
