package com.xkball.x3dmap.mixin;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.xkball.x3dmap.X3dMap;
import com.xkball.x3dmap.client.b3d.pipeline.X3dMapPipelineModifiers;
import com.xkball.x3dmap.client.render.ber.TerrainProjectorBlockEntityRenderer;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.client.renderer.LevelRenderer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

@NonNullByDefault
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {

    @Unique
    private static final @Nullable MethodHandle IRIS_SHADOWS_CURRENTLY_BEING_RENDERED = x3dmap$findIrisShadowStateHandler();

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void x3dmap$renderTerrainProjectors(CallbackInfo callbackInfo) {
        if (x3dmap$isIrisShadowPass()) {
            return;
        }
        TerrainProjectorBlockEntityRenderer.renderPending();
    }

    @Unique
    private static @Nullable MethodHandle x3dmap$findIrisShadowStateHandler() {
        try {
            return MethodHandles.lookup().findStatic(
                    Class.forName("net.irisshaders.iris.shadows.ShadowRenderingState", false, MixinLevelRenderer.class.getClassLoader()),
                    "areShadowsCurrentlyBeingRendered",
                    MethodType.methodType(boolean.class)
            );
        } catch (Exception exception) {
            return null;
        }
    }

    @Unique
    private static boolean x3dmap$isIrisShadowPass() {
        if (IRIS_SHADOWS_CURRENTLY_BEING_RENDERED == null) {
            return false;
        }
        try {
            return (boolean) IRIS_SHADOWS_CURRENTLY_BEING_RENDERED.invokeExact();
        } catch (Throwable exception) {
            X3dMap.LOGGER.error("Failed to query Iris shadow rendering state", exception);
            return false;
        }
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
