package com.xkball.x3dmap.client.render.pip.layers;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.x3dmap.api.client.render.PictureInPictureRenderLayer;
import com.xkball.x3dmap.client.render.pip.WorldTerrainPipRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;

public class CameraTargetRenderer implements PictureInPictureRenderLayer<WorldTerrainPipRenderer, WorldTerrainPipRenderer.WorldTerrainState> {
    @Override
    public String name() {
        return "cameraTarget";
    }
    
    @Override
    public void render(WorldTerrainPipRenderer pip, WorldTerrainPipRenderer.WorldTerrainState renderState, PoseStack poseStack, GpuTextureView texture, GpuTextureView depth) {
        poseStack.pushPose();
//        var buffer = pip.getBufferSource().getBuffer(RenderTypes.LINES);
        var buffer = pip.getBufferSource().getBuffer(RenderTypes.debugQuads());
        var dir = renderState.dirVec();
        var pose = poseStack.last();
        buffer.addVertex(pose,renderState.cameraTarget().x - 0.5f,-1000, renderState.cameraTarget().z).setColor(VanillaUtils.getColor(255,175,71, 255));
        buffer.addVertex(pose,renderState.cameraTarget().x + 0.5f,-1000, renderState.cameraTarget().z).setColor(VanillaUtils.getColor(255,175,71, 255));
        buffer.addVertex(pose,renderState.cameraTarget().x + 0.5f, 1000, renderState.cameraTarget().z).setColor(VanillaUtils.getColor(255,66,64, 255));
        buffer.addVertex(pose,renderState.cameraTarget().x - 0.5f, 1000, renderState.cameraTarget().z).setColor(VanillaUtils.getColor(255,66,64, 255));
        buffer.addVertex(pose,renderState.cameraTarget().x,-1000, renderState.cameraTarget().z - 0.5f).setColor(VanillaUtils.getColor(255,175,71, 255));
        buffer.addVertex(pose,renderState.cameraTarget().x,-1000, renderState.cameraTarget().z + 0.5f).setColor(VanillaUtils.getColor(255,175,71, 255));
        buffer.addVertex(pose,renderState.cameraTarget().x, 1000, renderState.cameraTarget().z + 0.5f).setColor(VanillaUtils.getColor(255,66,64, 255));
        buffer.addVertex(pose,renderState.cameraTarget().x, 1000, renderState.cameraTarget().z - 0.5f).setColor(VanillaUtils.getColor(255,66,64, 255));
//        buffer.addVertex(pose,renderState.cameraTarget().x,-1000, renderState.cameraTarget().z).setNormal(pose,-dir.x,0,-dir.z).setLineWidth(2).setColor(VanillaUtils.getColor(255,175,71, 255));
//        buffer.addVertex(pose,renderState.cameraTarget().x, 1000, renderState.cameraTarget().z).setNormal(pose,-dir.x,0,-dir.z).setLineWidth(2).setColor(VanillaUtils.getColor(255,66,64, 255));
        pip.getBufferSource().endLastBatch();
        poseStack.popPose();
    }
}
