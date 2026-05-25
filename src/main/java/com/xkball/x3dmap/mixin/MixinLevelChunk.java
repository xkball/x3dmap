package com.xkball.x3dmap.mixin;

import com.xkball.x3dmap.X3dMap;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public class MixinLevelChunk {

    @Inject(method = "markUnsaved", at = @At("RETURN"))
    public void onMarkDirty(CallbackInfo ci){
        X3dMap.MARK_DIRTY_CALLBACK.accept((LevelChunk)(Object) this);
    }
}
