package com.xkball.x3dmap.client.map.selection;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.xkball.x3dmap.api.client.render.PictureInPictureRenderLayer;
import com.xkball.x3dmap.client.b3d.pipeline.X3dMapRenderPipelines;
import com.xkball.x3dmap.client.render.pip.WorldTerrainPipRenderer;
import com.xkball.x3dmap.client.render.pip.layers.TerrainRenderer;
import com.xkball.x3dmap.client.terrain.TerrainChunkManager;
import com.xkball.x3dmap.client.terrain.TerrainTextureManager;
import com.xkball.xklibmc.api.client.b3d.SamplerCacheCache;
import com.xkball.xklibmc.api.client.mixin.IExtendedRenderPass;
import com.xkball.xklibmc.client.b3d.IndirectDrawCommand;
import com.xkball.xklibmc.utils.ClientUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class SelectionOverlayRenderer implements PictureInPictureRenderLayer<WorldTerrainPipRenderer, WorldTerrainPipRenderer.WorldTerrainState> {
    
    @Override
    public String name() {
        return "selection";
    }
    
    @Override
    public void render(WorldTerrainPipRenderer pip, WorldTerrainPipRenderer.WorldTerrainState renderState, PoseStack poseStack, GpuTextureView texture, GpuTextureView depth) {
        if (TerrainChunkManager.INSTANCE.compatibleMode) {
            return;
        }
        var selectionStorage = SelectionExtension.currentStorage();
        if (selectionStorage == null) {
            return;
        }
        var selectedChunks = selectionStorage.selectedChunks();
        if (selectedChunks.isEmpty()) {
            return;
        }
        var levelStorage = TerrainChunkManager.INSTANCE.getCurrentLevelChunkStorage();
        if (levelStorage == null) {
            return;
        }
        
        var groupedByTexture = new HashMap<TerrainTextureManager.VirtualTexturePos, ArrayList<IndirectDrawCommand>>();
        for (var chunkPos : selectedChunks) {
            var chunk = levelStorage.getChunk(chunkPos);
            if (chunk == null) {
                continue;
            }
            var uploadInfo = levelStorage.terrainTextureManager.getUploadInfo(chunkPos);
            groupedByTexture.computeIfAbsent(uploadInfo.texturePos(), _ -> new ArrayList<>())
                    .add(new IndirectDrawCommand(TerrainRenderer.LODS[0].getIndexCount(), 1, 0, 0, 0,
                            chunkPos.getMinBlockX(), chunkPos.getMinBlockZ()));
        }
        
        if (groupedByTexture.isEmpty()) {
            return;
        }
        
        var modelView = RenderSystem.getModelViewStack().mul(poseStack.last().pose(), new org.joml.Matrix4f());
        var transformUBO = RenderSystem.getDynamicUniforms().writeTransform(modelView, new org.joml.Vector4f(1, 1, 1, 1), new org.joml.Vector3f(), new org.joml.Matrix4f());
        
        for (var entry : groupedByTexture.entrySet()) {
            var textures = levelStorage.terrainTextureManager.getTextures(entry.getKey());
            var commands = entry.getValue();
            var commandBuffer = IndirectDrawCommand.buildCommandList(commands);
            
            try (var renderpass = ClientUtils.getCommandEncoder().createRenderPass(
                    () -> "selection overlay rendering", texture, OptionalInt.empty(), depth, OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(renderpass);
                renderpass.setPipeline(X3dMapRenderPipelines.SELECTION_OVERLAY);
                renderpass.setUniform("DynamicTransforms", transformUBO);
                renderpass.setVertexBuffer(0, TerrainRenderer.LODS[0].getVertexBuffer());
                renderpass.setIndexBuffer(TerrainRenderer.LODS[0].getIndexBuffer(), TerrainRenderer.LODS[0].getIndexType());
                renderpass.bindTexture("heightTexture", textures.depthTextureView(), SamplerCacheCache.NEAREST_REPEAT);
                IExtendedRenderPass.cast(renderpass).xklib$setSSBO("cmd", commandBuffer.slice());
                IExtendedRenderPass.cast(renderpass).xklib$multiDrawElementsIndirect(commandBuffer, commands.size());
            }
        }
    }
}
