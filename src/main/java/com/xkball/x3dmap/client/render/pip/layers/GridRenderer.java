package com.xkball.x3dmap.client.render.pip.layers;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xkball.x3dmap.api.client.render.PictureInPictureRenderLayer;
import com.xkball.x3dmap.client.render.pip.WorldTerrainPipRenderer;
import com.xkball.xklibmc.api.client.mixin.IExtendedBufferBuilder;
import com.xkball.xklibmc.client.b3d.uniform.XKLibUniforms;
import com.xkball.xklibmc.x3d.backend.b3d.pipeline.B3dRenderPipelines;
import com.xkball.xklibmc.x3d.backend.b3d.vertex.B3dVertexFormats;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

public class GridRenderer implements PictureInPictureRenderLayer<WorldTerrainPipRenderer, WorldTerrainPipRenderer.WorldTerrainState> {
    
    @Override
    public String name() {
        return "grid";
    }
    
    @Override
    public void render(WorldTerrainPipRenderer pip, WorldTerrainPipRenderer.WorldTerrainState renderState, PoseStack poseStack, GpuTextureView texture, GpuTextureView depth) {
        poseStack.pushPose();
        XKLibUniforms.SCREEN_SIZE.startOverride(
                b -> b.putVec2(texture.getWidth(0), texture.getHeight(0)));
        var step = 512;
        poseStack.translate(renderState.cameraTarget().x - (renderState.cameraTarget().x % step), 0, renderState.cameraTarget().z - (renderState.cameraTarget().z % step));
        
        var matrix = poseStack.last();
        var y = (float) renderState.centerPos().getY();
        var max = 8192 * 8;
        var min = -max;
        var xColor = 0xFF173A8F;
        var zColor = 0xFF12337F;
        
        var renderType = B3dRenderPipelines.LINE.asRenderType();
        var bufferConsumer = pip.getBufferSource().getBuffer(renderType);
        if (bufferConsumer instanceof IExtendedBufferBuilder buffer) {
            for (int x = min; x <= max; x += step) {
                drawLine3D(bufferConsumer, buffer, matrix, x, y, min, x, y, max, xColor);
            }
            
            for (int z = min; z <= max; z += step) {
                drawLine3D(bufferConsumer, buffer, matrix, min, y, z, max, y, z, zColor);
            }
        }
        
        pip.getBufferSource().endLastBatch();
        poseStack.popPose();
        XKLibUniforms.SCREEN_SIZE.endOverride();
    }
    
    private static void drawLine3D(VertexConsumer vertexConsumer, IExtendedBufferBuilder buffer, PoseStack.Pose matrix, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
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
        
        vertexConsumer.addVertex(matrix, x0, y0, z0).setColor(color);
        writeLineAttribs(buffer, p0x, p0y, p0z, p1x, p1y, p1z, -1.0f, 0.0f);
        
        vertexConsumer.addVertex(matrix, x0, y0, z0).setColor(color);
        writeLineAttribs(buffer, p0x, p0y, p0z, p1x, p1y, p1z, 1.0f, 0.0f);
        
        vertexConsumer.addVertex(matrix, x1, y1, z1).setColor(color);
        writeLineAttribs(buffer, p0x, p0y, p0z, p1x, p1y, p1z, -1.0f, 1.0f);
        
        vertexConsumer.addVertex(matrix, x1, y1, z1).setColor(color);
        writeLineAttribs(buffer, p0x, p0y, p0z, p1x, p1y, p1z, -1.0f, 1.0f);
        
        vertexConsumer.addVertex(matrix, x1, y1, z1).setColor(color);
        writeLineAttribs(buffer, p0x, p0y, p0z, p1x, p1y, p1z, 1.0f, 1.0f);
        
        vertexConsumer.addVertex(matrix, x0, y0, z0).setColor(color);
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
