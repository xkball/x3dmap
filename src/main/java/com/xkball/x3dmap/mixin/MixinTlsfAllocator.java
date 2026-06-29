package com.xkball.x3dmap.mixin;

import com.mojang.blaze3d.vertex.TlsfAllocator;
import com.xkball.x3dmap.api.mixin.IExtendedTlsfAllocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TlsfAllocator.class)
public class MixinTlsfAllocator {
    
    @Inject(method = "allocate",at = @At(value = "RETURN", ordinal = 1))
    public void onAlloc(long size, int align, CallbackInfoReturnable<TlsfAllocator.Allocation> cir){
        IExtendedTlsfAllocation.cast(cir.getReturnValue()).setX3dmap$requiedSize(size);
    }
}
