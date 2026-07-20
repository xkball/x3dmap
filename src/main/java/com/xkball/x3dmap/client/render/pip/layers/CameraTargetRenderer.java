package com.xkball.x3dmap.client.render.pip.layers;

import com.xkball.x3dmap.api.client.render.IMap3dLayer;
import com.xkball.x3dmap.api.client.render.IMap3dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMap3dRenderContext;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.client.renderer.rendertype.RenderTypes;

@NonNullByDefault
public class CameraTargetRenderer implements IMap3dLayer {

    @Override
    public IMap3dRenderCommand prepareRender(IMapFrame frame) {
        return this::render;
    }

    private void render(IMap3dRenderContext context) {
        var poseStack = context.poseStack();
        poseStack.pushPose();
//        var buffer = context.bufferSource().getBuffer(RenderTypes.LINES);
        var buffer = context.bufferSource().getBuffer(RenderTypes.debugQuads());
        var camera = context.frame().camera();
        var targetX = camera.targetX();
        var targetZ = camera.targetZ();
        var pose = poseStack.last();
        buffer.addVertex(pose, targetX - 0.5f, -1000, targetZ).setColor(VanillaUtils.getColor(255, 175, 71, 255));
        buffer.addVertex(pose, targetX + 0.5f, -1000, targetZ).setColor(VanillaUtils.getColor(255, 175, 71, 255));
        buffer.addVertex(pose, targetX + 0.5f, 1000, targetZ).setColor(VanillaUtils.getColor(255, 66, 64, 255));
        buffer.addVertex(pose, targetX - 0.5f, 1000, targetZ).setColor(VanillaUtils.getColor(255, 66, 64, 255));
        buffer.addVertex(pose, targetX, -1000, targetZ - 0.5f).setColor(VanillaUtils.getColor(255, 175, 71, 255));
        buffer.addVertex(pose, targetX, -1000, targetZ + 0.5f).setColor(VanillaUtils.getColor(255, 175, 71, 255));
        buffer.addVertex(pose, targetX, 1000, targetZ + 0.5f).setColor(VanillaUtils.getColor(255, 66, 64, 255));
        buffer.addVertex(pose, targetX, 1000, targetZ - 0.5f).setColor(VanillaUtils.getColor(255, 66, 64, 255));
//        buffer.addVertex(pose,targetX,-1000, targetZ).setNormal(pose,-dir.x(),0,-dir.z()).setLineWidth(2).setColor(VanillaUtils.getColor(255,175,71, 255));
//        buffer.addVertex(pose,targetX, 1000, targetZ).setNormal(pose,-dir.x(),0,-dir.z()).setLineWidth(2).setColor(VanillaUtils.getColor(255,66,64, 255));
        context.bufferSource().endLastBatch();
        poseStack.popPose();
    }
}
