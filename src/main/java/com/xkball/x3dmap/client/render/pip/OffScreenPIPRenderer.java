package com.xkball.x3dmap.client.render.pip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

public abstract class OffScreenPIPRenderer<T extends PictureInPictureRenderState> extends PictureInPictureRenderer<T> {

    public OffScreenPIPRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    public void renderToExternalTexture(T renderState, GpuTextureView colorTexture, GpuTextureView depthTexture) {
        var prevColor = RenderSystem.outputColorTextureOverride;
        var prevDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = colorTexture;
        RenderSystem.outputDepthTextureOverride = depthTexture;
        try {
            this.renderToTexture(renderState, new PoseStack());
        } finally {
            RenderSystem.outputColorTextureOverride = prevColor;
            RenderSystem.outputDepthTextureOverride = prevDepth;
        }
    }
}
