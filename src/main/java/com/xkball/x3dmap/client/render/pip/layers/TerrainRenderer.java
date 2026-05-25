package com.xkball.x3dmap.client.render.pip.layers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.xkball.xklibmc.api.client.b3d.SamplerCacheCache;
import com.xkball.xklibmc.api.client.mixin.IExtendedRenderPass;
import com.xkball.xklibmc.client.b3d.mesh.CachedMesh;
import com.xkball.xklibmc.client.b3d.postprocess.XKLibPostProcesses;
import com.xkball.xklibmc.client.b3d.uniform.XKLibUniforms;
import com.xkball.xklibmc.utils.ClientUtils;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.x3dmap.api.client.render.PictureInPictureRenderLayer;
import com.xkball.x3dmap.client.b3d.X3dMapUniforms;
import com.xkball.x3dmap.client.b3d.pipeline.X3dMapRenderPipelines;
import com.xkball.x3dmap.client.terrain.TerrainChunkManager;
import com.xkball.x3dmap.client.render.pip.WorldTerrainPipRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

public class TerrainRenderer implements PictureInPictureRenderLayer<WorldTerrainPipRenderer, WorldTerrainPipRenderer.WorldTerrainState> {
    
    public static final CachedMesh CUBE = new CachedMesh("cube", X3dMapRenderPipelines.WORLD_TERRAIN_PIP,TerrainRenderer::createCubeMesh,true).setCloseOnExit();
    public static final CachedMesh CHUNK1 = new CachedMesh("lod1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 16, 1),true).setCloseOnExit();
    public static final CachedMesh REGION1 = new CachedMesh("region1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 512, 1),true).setCloseOnExit();
    public static final CachedMesh REGION2 = new CachedMesh("region1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 512, 2),true).setCloseOnExit();
    public static final CachedMesh REGION3 = new CachedMesh("region1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 512, 4),true).setCloseOnExit();
    public static final CachedMesh REGION4 = new CachedMesh("region1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 512, 8),true).setCloseOnExit();
    public static final CachedMesh[] LODS = new CachedMesh[]{CHUNK1, REGION1, REGION2, REGION3, REGION4};
    
    @Override
    public String name() {
        return "terrain";
    }
    
    @Override
    public void render(WorldTerrainPipRenderer pip, WorldTerrainPipRenderer.WorldTerrainState renderState, PoseStack poseStack, GpuTextureView texture, GpuTextureView depth) {
        var level = Minecraft.getInstance().level;
        if(level == null) return;
        RenderSystem.getModelViewStack().pushMatrix();
        var modelView = RenderSystem.getModelViewStack().mul(poseStack.last().pose(), new Matrix4f());
        var frustum = new Frustum(modelView,renderState.projMatrix());
        var transformUBO = RenderSystem.getDynamicUniforms().writeTransform(modelView, new Vector4f(1,1,1,1), new Vector3f(), new Matrix4f());
        X3dMapRenderPipelines.PHONE_LIGHT.updateUnsafe(b ->
                b.putVec3(VanillaUtils.dirVec(Mth.clamp(renderState.xRot(),45,90),renderState.yRot() + 2))
                 .putVec3(renderState.cameraPos()));
        X3dMapUniforms.LEVEL_DATA.updateUnsafe(b -> {
            b.putFloat(level.getMinY());
            b.putFloat(level.getMaxY());
            b.putFloat(level.getSeaLevel());
            b.putFloat(0);
        });
        if(TerrainChunkManager.INSTANCE.compatibleMode){
            try(var renderInfo = renderState.minimap()
                    ? TerrainChunkManager.INSTANCE.gatherRenderInfoCompatibleModeMinimap(frustum, renderState.cullNear(), renderState.cameraOffset().add(renderState.cameraTarget()), renderState.cameraTarget(), renderState.minimapHighDetailRangeChunks())
                    : TerrainChunkManager.INSTANCE.gatherRenderInfoCompatibleMode(frustum, renderState.cullNear(), renderState.cameraOffset().add(renderState.cameraTarget()), renderState.cameraTarget(), renderState.lodDistance())){
                if(!renderInfo.lodFullMesh().isEmpty()){
                    try (var renderpass = ClientUtils.getCommandEncoder().createRenderPass(() -> "world terrain pip rendering lod full mesh", texture, OptionalInt.empty(), depth, OptionalDouble.empty())){
                        RenderSystem.bindDefaultUniforms(renderpass);
                        renderpass.setPipeline(X3dMapRenderPipelines.WORLD_TERRAIN_PIP_FULL_MESH);
                        renderpass.setUniform("DynamicTransforms", transformUBO);
                        var indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.TRIANGLES);
                        renderpass.setIndexBuffer(indexBuffer.getBuffer(64 * 1024 * 1024 / 20),indexBuffer.type());
                        for(var infoBlock : renderInfo.lodFullMesh()){
                            renderpass.setVertexBuffer(0, infoBlock.drawBuffer());
                            for(var cmd : infoBlock.drawCommands()){
                                renderpass.drawIndexed(cmd.baseVertex(), cmd.firstIndex(), cmd.count(), cmd.instanceCount());
                            }
                        }
                    }
                }
            }
        }
        else {
            try(var renderInfo = TerrainChunkManager.INSTANCE.gatherRenderInfo(frustum, renderState.cullNear(), renderState.cameraOffset().add(renderState.cameraTarget()), renderState.cameraTarget(),renderState.minimap() ? renderState.minimapHighDetailRangeChunks() * 16 : renderState.lodDistance())){
                if(renderInfo.blocks() != null){
                    try (var renderpass = ClientUtils.getCommandEncoder().createRenderPass(() -> "world terrain pip rendering", texture, OptionalInt.empty(), depth, OptionalDouble.empty())){
                        RenderSystem.bindDefaultUniforms(renderpass);
                        renderpass.setPipeline(X3dMapRenderPipelines.WORLD_TERRAIN_PIP);
                        renderpass.setUniform("DynamicTransforms", transformUBO);
                        renderpass.setVertexBuffer(0, CUBE.getVertexBuffer());
                        renderpass.setIndexBuffer(CUBE.getIndexBuffer(),CUBE.getIndexType());
                        for(var infoBlock : renderInfo.blocks()){
                            IExtendedRenderPass.cast(renderpass).xklib$setSSBO("ABlock",infoBlock.blockDataBuffer().slice());
                            IExtendedRenderPass.cast(renderpass).xklib$setSSBO("FaceIndex",infoBlock.faceIndexBuffer().slice());
                            IExtendedRenderPass.cast(renderpass).xklib$multiDrawElementsIndirect(infoBlock.commandBuffer(), infoBlock.drawCount());
                        }
                    }
                }
                if(renderInfo.lods() != null){
                    try (var renderpass = ClientUtils.getCommandEncoder().createRenderPass(() -> "world terrain pip rendering lod", texture, OptionalInt.empty(), depth, OptionalDouble.empty())){
                        RenderSystem.bindDefaultUniforms(renderpass);
                        renderpass.setPipeline(X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD);
                        renderpass.setUniform("DynamicTransforms", transformUBO);
                        for(var infoBlock : renderInfo.lods()){
//                            if(infoBlock.lod() > 0) continue;
                            var mesh = LODS[infoBlock.lod()];
                            renderpass.setVertexBuffer(0, mesh.getVertexBuffer());
                            renderpass.setIndexBuffer(mesh.getIndexBuffer(),mesh.getIndexType());
                            renderpass.bindTexture("colorTexture", infoBlock.texture().colorTextureView(), SamplerCacheCache.NEAREST_REPEAT);
                            renderpass.bindTexture("heightTexture", infoBlock.texture().depthTextureView(), SamplerCacheCache.NEAREST_REPEAT);
                            IExtendedRenderPass.cast(renderpass).xklib$setSSBO("cmd",infoBlock.commandBuffer().slice());
                            IExtendedRenderPass.cast(renderpass).xklib$multiDrawElementsIndirect(infoBlock.commandBuffer(), infoBlock.drawCount());
                        }
                    }
                }
            }

        }
        var cp = renderState.cameraPos();
        XKLibUniforms.INVERSE_PROJ_MAT.updateUnsafe(b -> {
            b.putMat4f(renderState.projMatrix().invert(new Matrix4f()));
            b.putMat4f(renderState.projMatrix());
            b.putVec4(new Vector4f(renderState.dirVec(),1));
            b.putVec4(new Vector4f(-cp.x,-cp.y,-cp.z,1));
        });
//            XKLibPostProcesses.SSAO.apply(texture, depth);
        XKLibPostProcesses.SSR.apply(texture, depth);
        RenderSystem.getModelViewStack().popMatrix();
    }
    
    private static void createCubeMesh(BufferBuilder builder){
        //down
        builder.addVertex(0.0f, 0.0f, 0.0f).setColor(-1).setNormal(0, -1, 0);
        builder.addVertex(1.0f, 0.0f, 0.0f).setColor(-1).setNormal(0, -1, 0);
        builder.addVertex(1.0f, 0.0f, 1.0f).setColor(-1).setNormal(0, -1, 0);
        builder.addVertex(0.0f, 0.0f, 0.0f).setColor(-1).setNormal(0, -1, 0);
        builder.addVertex(1.0f, 0.0f, 1.0f).setColor(-1).setNormal(0, -1, 0);
        builder.addVertex(0.0f, 0.0f, 1.0f).setColor(-1).setNormal(0, -1, 0);
        //up
        builder.addVertex(0.0f, 1.0f, 1.0f).setColor(-1).setNormal(0, 1, 0);
        builder.addVertex(1.0f, 1.0f, 1.0f).setColor(-1).setNormal(0, 1, 0);
        builder.addVertex(1.0f, 1.0f, 0.0f).setColor(-1).setNormal(0, 1, 0);
        builder.addVertex(0.0f, 1.0f, 1.0f).setColor(-1).setNormal(0, 1, 0);
        builder.addVertex(1.0f, 1.0f, 0.0f).setColor(-1).setNormal(0, 1, 0);
        builder.addVertex(0.0f, 1.0f, 0.0f).setColor(-1).setNormal(0, 1, 0);
        //north
        builder.addVertex(1.0f, 0.0f, 0.0f).setColor(-1).setNormal(0, 0, -1);
        builder.addVertex(0.0f, 0.0f, 0.0f).setColor(-1).setNormal(0, 0, -1);
        builder.addVertex(0.0f, 1.0f, 0.0f).setColor(-1).setNormal(0, 0, -1);
        builder.addVertex(1.0f, 0.0f, 0.0f).setColor(-1).setNormal(0, 0, -1);
        builder.addVertex(0.0f, 1.0f, 0.0f).setColor(-1).setNormal(0, 0, -1);
        builder.addVertex(1.0f, 1.0f, 0.0f).setColor(-1).setNormal(0, 0, -1);
        //south
        builder.addVertex(0.0f, 0.0f, 1.0f).setColor(-1).setNormal(0, 0, 1);
        builder.addVertex(1.0f, 0.0f, 1.0f).setColor(-1).setNormal(0, 0, 1);
        builder.addVertex(1.0f, 1.0f, 1.0f).setColor(-1).setNormal(0, 0, 1);
        builder.addVertex(0.0f, 0.0f, 1.0f).setColor(-1).setNormal(0, 0, 1);
        builder.addVertex(1.0f, 1.0f, 1.0f).setColor(-1).setNormal(0, 0, 1);
        builder.addVertex(0.0f, 1.0f, 1.0f).setColor(-1).setNormal(0, 0, 1);
        //west
        builder.addVertex(0.0f, 0.0f, 0.0f).setColor(-1).setNormal(-1, 0, 0);
        builder.addVertex(0.0f, 0.0f, 1.0f).setColor(-1).setNormal(-1, 0, 0);
        builder.addVertex(0.0f, 1.0f, 1.0f).setColor(-1).setNormal(-1, 0, 0);
        builder.addVertex(0.0f, 0.0f, 0.0f).setColor(-1).setNormal(-1, 0, 0);
        builder.addVertex(0.0f, 1.0f, 1.0f).setColor(-1).setNormal(-1, 0, 0);
        builder.addVertex(0.0f, 1.0f, 0.0f).setColor(-1).setNormal(-1, 0, 0);
        //east
        builder.addVertex(1.0f, 0.0f, 1.0f).setColor(-1).setNormal(1, 0, 0);
        builder.addVertex(1.0f, 0.0f, 0.0f).setColor(-1).setNormal(1, 0, 0);
        builder.addVertex(1.0f, 1.0f, 0.0f).setColor(-1).setNormal(1, 0, 0);
        builder.addVertex(1.0f, 0.0f, 1.0f).setColor(-1).setNormal(1, 0, 0);
        builder.addVertex(1.0f, 1.0f, 0.0f).setColor(-1).setNormal(1, 0, 0);
        builder.addVertex(1.0f, 1.0f, 1.0f).setColor(-1).setNormal(1, 0, 0);
    }
    
    private static void createLodMesh(BufferBuilder builder,int side, int step){
        for (int dx = 0; dx < side; dx += step) {
            for (int dz = 0; dz < side; dz += step) {
                builder.addVertex(dx,      0, dz);
                builder.addVertex(dx,      0, dz+step);
                builder.addVertex(dx+step, 0, dz);
                builder.addVertex(dx+step, 0, dz);
                builder.addVertex(dx,      0, dz+step);
                builder.addVertex(dx+step, 0, dz+step);
            }
        }
    }
}
