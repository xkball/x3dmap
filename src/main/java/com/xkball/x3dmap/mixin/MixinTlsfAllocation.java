package com.xkball.x3dmap.mixin;

import com.mojang.blaze3d.vertex.TlsfAllocator;
import com.xkball.x3dmap.api.mixin.IExtendedTlsfAllocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(TlsfAllocator.Allocation.class)
public class MixinTlsfAllocation implements IExtendedTlsfAllocation {
    
    @Unique
    private long x3dmap$requiedSize = 0;
    
    @Override
    public void setX3dmap$requiedSize(long x3dmap$requiedSize) {
        this.x3dmap$requiedSize = x3dmap$requiedSize;
    }
    
    @Override
    public long getX3dmap$requiedSize() {
        return x3dmap$requiedSize;
    }
}
