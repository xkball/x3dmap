package com.xkball.x3dmap.api.client.render;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.level.CameraRenderState;

@NonNullByDefault
public interface IMap3dRenderContext {

    IMapFrame frame();

    PoseStack poseStack();

    MultiBufferSource.BufferSource bufferSource();

    CameraRenderState cameraRenderState();

    GpuTextureView colorTarget();

    GpuTextureView depthTarget();
}
