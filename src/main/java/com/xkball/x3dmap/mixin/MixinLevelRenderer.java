package com.xkball.x3dmap.mixin;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.xkball.x3dmap.client.b3d.pipeline.X3dMapPipelineModifiers;
import com.xkball.x3dmap.client.render.ber.TerrainProjectorBlockEntityRenderer;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@NonNullByDefault
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void x3dmap$renderTerrainProjectors(CallbackInfo callbackInfo) {
        TerrainProjectorBlockEntityRenderer.renderPending();
    }

    @Inject(method = "lambda$addLateDebugPass$0",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getInstance()Lnet/minecraft/client/Minecraft;"))
    private void x3dmap$beginAlwaysOnTopRendering(CallbackInfo callbackInfo) {
        RenderSystem.pushPipelineModifier(X3dMapPipelineModifiers.NO_DEPTH_TEST);
    }

    @Redirect(method = "lambda$addLateDebugPass$0",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"))
    private void x3dmap$preserveMainDepth(CommandEncoder commandEncoder, GpuTexture depthTexture, double clearDepth) {
    }

    @Inject(method = "lambda$addLateDebugPass$0",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V", shift = At.Shift.AFTER))
    private void x3dmap$endAlwaysOnTopRendering(CallbackInfo callbackInfo) {
        RenderSystem.popPipelineModifier();
    }
}
