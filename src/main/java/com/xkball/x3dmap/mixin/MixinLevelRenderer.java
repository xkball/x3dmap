package com.xkball.x3dmap.mixin;

import com.xkball.x3dmap.client.render.ber.TerrainProjectorBlockEntityRenderer;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@NonNullByDefault
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void x3dmap$renderTerrainProjectors(CallbackInfo callbackInfo) {
        TerrainProjectorBlockEntityRenderer.renderPending();
    }
}
