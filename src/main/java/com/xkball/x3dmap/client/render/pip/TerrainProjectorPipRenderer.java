package com.xkball.x3dmap.client.render.pip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.utils.ClientUtils;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jspecify.annotations.Nullable;

@NonNullByDefault
public final class TerrainProjectorPipRenderer extends PictureInPictureRenderer<TerrainProjectorPipRenderer.TerrainProjectorState> {

    public TerrainProjectorPipRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<TerrainProjectorState> getRenderStateClass() {
        return TerrainProjectorState.class;
    }

    @Override
    protected void renderToTexture(TerrainProjectorState renderState, PoseStack poseStack) {
        ClientUtils.getCommandEncoder().clearColorTexture(RenderSystem.outputColorTextureOverride.texture(), 0xff000000);
        ClientUtils.getCommandEncoder().clearDepthTexture(RenderSystem.outputDepthTextureOverride.texture(), 1.0);
    }

    @Override
    protected String getTextureLabel() {
        return "terrain projector";
    }

    public record TerrainProjectorState(
            int x0,
            int x1,
            int y0,
            int y1,
            float scale,
            @Nullable ScreenRectangle scissorArea,
            @Nullable ScreenRectangle bounds
    ) implements PictureInPictureRenderState {
    }
}
