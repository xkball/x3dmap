package com.xkball.x3dmap.api.client.render;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

public interface PictureInPictureRenderLayer<R extends PictureInPictureRenderer<T>, T extends PictureInPictureRenderState> {
    
    String name();
    
    void render(R pip, T renderState, PoseStack poseStack, GpuTextureView texture, GpuTextureView depth);
}
