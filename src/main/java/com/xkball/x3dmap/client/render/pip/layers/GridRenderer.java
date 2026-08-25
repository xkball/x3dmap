package com.xkball.x3dmap.client.render.pip.layers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.xkball.x3dmap.api.client.render.IMap3dLayer;
import com.xkball.x3dmap.api.client.render.IMap3dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMap3dRenderContext;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.x3dmap.client.b3d.pipeline.X3dMapRenderPipelines;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.api.client.mixin.IExtendedBufferBuilder;
import com.xkball.xklibmc.client.b3d.mesh.CachedMesh;
import com.xkball.xklibmc.client.b3d.uniform.XKLibUniforms;
import com.xkball.xklibmc.x3d.backend.b3d.pipeline.B3dRenderPipelines;
import com.xkball.xklibmc.x3d.backend.b3d.vertex.B3dVertexFormats;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

@NonNullByDefault
public class GridRenderer implements IMap3dLayer {
    
    private static final CachedMesh GRID_MESH = new CachedMesh("grid", X3dMapRenderPipelines.LINE_IN_RANGE, bufferBuilder -> {
        var step = 512;
        var matrix = new PoseStack.Pose();
        var y = (float) 0;
        var max = 8192 * 8;
        var min = -max;
        var xColor = 0xFF173A8F;
        var zColor = 0xFF12337F;
        for (int x = min; x <= max; x += step) {
            tryDrawLine3D(bufferBuilder, matrix, x, y, min, x, y, max, xColor, xColor);
        }
        
        for (int z = min; z <= max; z += step) {
            tryDrawLine3D(bufferBuilder, matrix, min, y, z, max, y, z, zColor, zColor);
        }
    }, true).setCloseOnExit();

    @Override
    public IMap3dRenderCommand prepareRender(IMapFrame frame) {
        return this::render;
    }

    private void render(IMap3dRenderContext context) {
        var frame = context.frame();
        var poseStack = context.poseStack();
        var texture = context.colorTarget();
        poseStack.pushPose();
        var l = frame.camera().distance();
        updateFaceStartEnd(Math.max(1024 * 1024, l * l * 4), Math.max(2048 * 2048, l * l * 16));
        XKLibUniforms.SCREEN_SIZE.startOverride(b -> b.putVec2(texture.getWidth(0), texture.getHeight(0)));
        var step = 512;
        var camera = frame.camera();
        poseStack.translate(camera.targetX() - (camera.targetX() % step), 0, camera.targetZ() - (camera.targetZ() % step));
        GRID_MESH.render(X3dMapRenderPipelines.LINE_IN_RANGE, poseStack);
        poseStack.popPose();
        XKLibUniforms.SCREEN_SIZE.endOverride();
    }
    
    public static void updateFaceStartEnd(float start, float end) {
        X3dMapRenderPipelines.FADE.updateUnsafe(builder -> builder.putFloat(start).putFloat(end));
    }
    
    public static void tryDrawLine3D(VertexConsumer vertexConsumer, PoseStack.Pose matrix, float x0, float y0, float z0, float x1, float y1, float z1, int color1, int color2) {
        if(!(vertexConsumer instanceof IExtendedBufferBuilder buffer)) return;
        Vector3f p0 = new Vector3f(x0, y0, z0);
        matrix.pose().transformPosition(p0);
        Vector3f p1 = new Vector3f(x1, y1, z1);
        matrix.pose().transformPosition(p1);
        
        float p0x = p0.x();
        float p0y = p0.y();
        float p0z = p0.z();
        float p1x = p1.x();
        float p1y = p1.y();
        float p1z = p1.z();
        
        vertexConsumer.addVertex(matrix, x0, y0, z0).setColor(color1);
        writeLineAttribs(buffer, p0x, p0y, p0z, p1x, p1y, p1z, -1.0f, 0.0f);
        
        vertexConsumer.addVertex(matrix, x0, y0, z0).setColor(color1);
        writeLineAttribs(buffer, p0x, p0y, p0z, p1x, p1y, p1z, 1.0f, 0.0f);
        
        vertexConsumer.addVertex(matrix, x1, y1, z1).setColor(color2);
        writeLineAttribs(buffer, p0x, p0y, p0z, p1x, p1y, p1z, -1.0f, 1.0f);
        
        vertexConsumer.addVertex(matrix, x1, y1, z1).setColor(color2);
        writeLineAttribs(buffer, p0x, p0y, p0z, p1x, p1y, p1z, -1.0f, 1.0f);
        
        vertexConsumer.addVertex(matrix, x1, y1, z1).setColor(color2);
        writeLineAttribs(buffer, p0x, p0y, p0z, p1x, p1y, p1z, 1.0f, 1.0f);
        
        vertexConsumer.addVertex(matrix, x0, y0, z0).setColor(color1);
        writeLineAttribs(buffer, p0x, p0y, p0z, p1x, p1y, p1z, 1.0f, 0.0f);
    }

    private static void writeLineAttribs(IExtendedBufferBuilder buffer, float p0x, float p0y, float p0z, float p1x, float p1y, float p1z, float cornerX, float cornerY) {
        buffer.setUnsafe(B3dVertexFormats.P0, ptr -> {
            MemoryUtil.memPutFloat(ptr, p0x);
            MemoryUtil.memPutFloat(ptr + 4, p0y);
            MemoryUtil.memPutFloat(ptr + 8, p0z);
        });
        buffer.setUnsafe(B3dVertexFormats.P1, ptr -> {
            MemoryUtil.memPutFloat(ptr, p1x);
            MemoryUtil.memPutFloat(ptr + 4, p1y);
            MemoryUtil.memPutFloat(ptr + 8, p1z);
        });
        buffer.setUnsafe(B3dVertexFormats.CORNER, ptr -> {
            MemoryUtil.memPutFloat(ptr, cornerX);
            MemoryUtil.memPutFloat(ptr + 4, cornerY);
        });
    }
}
