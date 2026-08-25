package com.xkball.x3dmap.mixin;

import com.xkball.x3dmap.client.event.CancelWorldRenderingEvent;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@NonNullByDefault
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @ModifyVariable(
            method = {"update", "extract", "render"},
            at = @At("STORE"),
            name = "shouldRenderLevel"
    )
    private boolean template$cancelWorldRendering(boolean shouldRenderLevel) {
        if (!shouldRenderLevel) {
            return false;
        }
        return !NeoForge.EVENT_BUS.post(new CancelWorldRenderingEvent()).isCanceled();
    }
}
