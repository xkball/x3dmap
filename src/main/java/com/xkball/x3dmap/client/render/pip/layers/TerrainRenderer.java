package com.xkball.x3dmap.client.render.pip.layers;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.xkball.x3dmap.api.client.render.IMap3dLayer;
import com.xkball.x3dmap.api.client.render.IMap3dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMap3dRenderContext;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.x3dmap.client.b3d.pipeline.X3dMapRenderPipelines;
import com.xkball.x3dmap.client.terrain.CompatibilityTextureManager;
import com.xkball.x3dmap.client.terrain.TerrainMapManager;
import com.xkball.x3dmap.client.terrain.file.MapLevel;
import com.xkball.x3dmap.client.terrain.render.GpuNodeModel;
import com.xkball.x3dmap.client.terrain.render.MapNodeModel;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.api.client.mixin.IExtendedRenderPass;
import com.xkball.xklibmc.client.b3d.IndirectDrawCommand;
import com.xkball.xklibmc.client.b3d.mesh.CachedMesh;
import com.xkball.xklibmc.utils.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;

@NonNullByDefault
public class TerrainRenderer implements IMap3dLayer {

    public static final CachedMesh CUBE = new CachedMesh("cube", X3dMapRenderPipelines.WORLD_TERRAIN_PIP, TerrainRenderer::createCubeMesh, true).setCloseOnExit();
//    public static final CachedMesh CHUNK1 = new CachedMesh("lod1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 16, 1), true).setCloseOnExit();
//    public static final CachedMesh REGION1 = new CachedMesh("region1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 512, 1), true).setCloseOnExit();
//    public static final CachedMesh REGION2 = new CachedMesh("region1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 512, 2), true).setCloseOnExit();
//    public static final CachedMesh REGION3 = new CachedMesh("region1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 512, 4), true).setCloseOnExit();
//    public static final CachedMesh REGION4 = new CachedMesh("region1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 512, 8), true).setCloseOnExit();
//    public static final CachedMesh[] LODS = new CachedMesh[]{CHUNK1, REGION1, REGION2, REGION3, REGION4};

    public static long computeTime = 0;
    
    @Override
    public IMap3dRenderCommand prepareRender(IMapFrame frame) {
        return this::render;
    }

    private void render(IMap3dRenderContext context) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var mapLevel = TerrainMapManager.INSTANCE.currentChunkStorage;
        if (mapLevel == null || !mapLevel.getLevel().equals(level.dimension().identifier())) return;
        if (TerrainMapManager.INSTANCE.compatibleMode) {
            this.renderCompatible(context, mapLevel);
            return;
        }
        var frame = context.frame();
        var camera = frame.camera();
        var cameraPosition = new Vector3f(frame.cameraPosition());
        var minNodeY = Math.floorDiv(mapLevel.getMinY(), 512);
        var maxNodeY = Math.floorDiv(mapLevel.getMaxY() - 1, 512);
//        var renderNodes = new ArrayList<RenderCandidate>();
        var time = System.nanoTime();
        var renderNodes = mapLevel.getRegions().stream().parallel().flatMap(r -> {
            var result = new ArrayList<RenderCandidate>();
            for (var nodeY = minNodeY; nodeY <= maxNodeY; nodeY++) {
                var y = nodeY * 512;
                var pos = new BlockPos(r.getMinX(), y, r.getMinZ());
                var model = mapLevel.getNodeModelOrLoad(pos, 4);
                if (model == null || model.isEmpty()) continue;
                var gpuModel = mapLevel.getGpuNodeAsync(pos, 4).getNow(null);
                if (gpuModel == null || gpuModel.allocation() == null) continue;
                this.collectNode(mapLevel, frame, pos, model, gpuModel, 4, result);
            }
            return result.stream();
        }).toList();
        computeTime = System.nanoTime() - time;
        if (renderNodes.isEmpty()) return;
        var batchesByBuffer = new IdentityHashMap<GpuBuffer, RenderBatchBuilder>();
        for (var renderNode : renderNodes) {
            var gpuModel = renderNode.model;
            assert gpuModel.allocation() != null;
            var blockBuffer = gpuModel.buffer().getGpuBuffer(gpuModel.allocation());
            var builder = batchesByBuffer.computeIfAbsent(blockBuffer, (_) -> new RenderBatchBuilder(renderNode.lodLevel));
            builder.submit(gpuModel);
        }
        if (batchesByBuffer.isEmpty()) return;
        batchesByBuffer.forEach((_, builder) -> builder.build());
        RenderSystem.getModelViewStack().pushMatrix();
        try {
            var modelView = RenderSystem.getModelViewStack().mul(context.poseStack().last().pose(), new Matrix4f());
            var transformUBO = RenderSystem.getDynamicUniforms().writeTransform(modelView, new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
            X3dMapRenderPipelines.PHONE_LIGHT.updateUnsafe(b -> b.putVec3(VanillaUtils.dirVec(Mth.clamp(camera.xRotation(), 45, 90), camera.yRotation() + 2)).putVec3(cameraPosition));
            try (var renderpass = ClientUtils.getCommandEncoder().createRenderPass(
                    () -> "world terrain rendering", context.colorTarget(), OptionalInt.empty(), context.depthTarget(), OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(renderpass);
                renderpass.setVertexBuffer(0, CUBE.getVertexBuffer());
                renderpass.setIndexBuffer(CUBE.getIndexBuffer(), CUBE.getIndexType());
                renderpass.setUniform("DynamicTransforms", transformUBO);
                for (var entry : batchesByBuffer.entrySet()) {
                    var batch = entry.getValue();
                    renderpass.setPipeline(X3dMapRenderPipelines.WORLD_TERRAIN_NEW[batch.lodLevel]);
                    IExtendedRenderPass.cast(renderpass).xklib$setSSBO("ABlock", entry.getKey().slice());
                    IExtendedRenderPass.cast(renderpass).xklib$setSSBO("CMD", Objects.requireNonNull(batch.cmdBuffer).slice());
                    IExtendedRenderPass.cast(renderpass).xklib$multiDrawElementsIndirect(batch.cmdBuffer, batch.idx);
                    
                }
            }
        } finally {
            RenderSystem.getModelViewStack().popMatrix();
            batchesByBuffer.forEach((_, builder) -> builder.close());
        }
        
    }

    private void renderCompatible(IMap3dRenderContext context, MapLevel mapLevel) {
        var textureManager = mapLevel.getCompatibilityTextureManager();
        if (textureManager == null) return;
        var pose = context.poseStack().last();
        for (var pos : textureManager.getTexturePositions()) {
            var minX = pos.minBlockX();
            var minZ = pos.minBlockZ();
            var maxX = minX + CompatibilityTextureManager.BLOCK_SIDE_LENGTH;
            var maxZ = minZ + CompatibilityTextureManager.BLOCK_SIDE_LENGTH;
            if (!context.frame().isVisible(new AABB(minX, -0.01, minZ, maxX, 0.01, maxZ))) continue;
            var textureId = textureManager.getOrLoad(pos);
            if (textureId == null) continue;
            var buffer = context.bufferSource().getBuffer(RenderTypes.entityCutout(textureId, false));
            buffer.addVertex(pose, minX, 0, maxZ)
                    .setColor(-1)
                    .setUv(0, 1)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(0x00F000F0)
                    .setNormal(pose, 0, -1, 0);
            buffer.addVertex(pose, maxX, 0, maxZ)
                    .setColor(-1)
                    .setUv(1, 1)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(0x00F000F0)
                    .setNormal(pose, 0, -1, 0);
            buffer.addVertex(pose, maxX, 0, minZ)
                    .setColor(-1)
                    .setUv(1, 0)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(0x00F000F0)
                    .setNormal(pose, 0, -1, 0);
            buffer.addVertex(pose, minX, 0, minZ)
                    .setColor(-1)
                    .setUv(0, 0)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(0x00F000F0)
                    .setNormal(pose, 0, -1, 0);
        }
    }
    
    private record RenderCandidate(BlockPos pos, int lodLevel, GpuNodeModel model) {
    }
    
    private static class RenderBatchBuilder implements AutoCloseable{
        private final int lodLevel;
        private final List<IndirectDrawCommand> cmd = new ArrayList<>();
        private @Nullable GpuBuffer cmdBuffer;
        private int idx = 0;
        
        private RenderBatchBuilder(int lodLevel) {
            this.lodLevel = lodLevel;
        }
        
        private void submit(GpuNodeModel model) {
            for (int i = 0; i < 6; i++) {
                this.cmd.add(new IndirectDrawCommand(6, model.len(), i * 6, 0, idx, i, model.offset()));
                this.idx += 1;
            }
        }
        
        private void build(){
            this.cmdBuffer = IndirectDrawCommand.buildCommandList(cmd);
        }
        
        
        @Override
        public void close() {
            if(cmdBuffer != null) {
                cmdBuffer.close();
            }
        }
    }

    private void collectNode(MapLevel mapLevel, IMapFrame frame, BlockPos pos,
                             MapNodeModel model, GpuNodeModel gpuModel, int lodLevel, ArrayList<RenderCandidate> renderNodes) {
        var bounds = nodeBounds(model);
        if (!frame.isVisible(bounds)) return;
        if (lodLevel == 0 || screenSize(frame, bounds) <= frame.lodThreshold()) {
            if (gpuModel.allocation() == null) return;
            renderNodes.add(new RenderCandidate(pos, lodLevel, gpuModel));
            return;
        }
        var childLodLevel = lodLevel - 1;
        var childSideLength = 1 << (model.depth - 1);
        var childModels = new MapNodeModel[8];
        var childGpuModels = new GpuNodeModel[8];
        var pending = false;
        var minX = model.x << model.depth;
        var minY = model.y << model.depth;
        var minZ = model.z << model.depth;
        var offsetX = new int[]{0,0,0,0,1,1,1,1};
        var offsetY = new int[]{0,0,1,1,0,0,1,1};
        var offsetZ = new int[]{0,1,0,1,0,1,0,1};
        for (int i = 0; i < 8; i++) {
            var childPos = new BlockPos(minX + offsetX[i] * childSideLength, minY + offsetY[i] * childSideLength, minZ + offsetZ[i] * childSideLength);
            var childModel = mapLevel.getNodeModelOrLoad(childPos, childLodLevel);
            if (childModel == null) {
                pending = true;
                break;
            } else {
                var childGpuModel = mapLevel.getGpuNodeAsync(childPos, childLodLevel).getNow(null);
                if (childGpuModel == null){
                    pending = true;
                    break;
                }
                else {
                    childModels[i] = childModel;
                    childGpuModels[i] = childGpuModel;
                }
            }
        }
        
        if (pending) {
            if (gpuModel.allocation() == null) return;
            renderNodes.add(new RenderCandidate(pos, lodLevel, gpuModel));
            return;
        }
        
        for (int i = 0; i < 8; i++) {
            var childModel = childModels[i];
            var childGpuModel = childGpuModels[i];
            if (childModel.isEmpty() || childGpuModel == null || gpuModel.allocation() == null) continue;
            var childPos = new BlockPos(minX + offsetX[i] * childSideLength, minY + offsetY[i] * childSideLength, minZ + offsetZ[i] * childSideLength);
            this.collectNode(mapLevel, frame, childPos, childModel, childGpuModel, childLodLevel, renderNodes);
        }
    }

    private static boolean isCompleted(CompletableFuture<?> future) {
        return future.isDone() && !future.isCancelled() && !future.isCompletedExceptionally();
    }

    private static AABB nodeBounds(MapNodeModel model) {
        var sideLength = 1 << model.depth;
        var minX = model.x << model.depth;
        var minY = model.y << model.depth;
        var minZ = model.z << model.depth;
        return new AABB(minX, minY, minZ, minX + sideLength, minY + sideLength, minZ + sideLength);
    }

    private static float screenSize(IMapFrame frame, AABB bounds) {
        var minX = Float.POSITIVE_INFINITY;
        var minY = Float.POSITIVE_INFINITY;
        var maxX = Float.NEGATIVE_INFINITY;
        var maxY = Float.NEGATIVE_INFINITY;
        for (var x = 0; x < 2; x++) {
            for (var y = 0; y < 2; y++) {
                for (var z = 0; z < 2; z++) {
                    var projected = frame.viewProjectionMatrix().transform(new Vector4f(
                            (float) (x == 0 ? bounds.minX : bounds.maxX),
                            (float) (y == 0 ? bounds.minY : bounds.maxY),
                            (float) (z == 0 ? bounds.minZ : bounds.maxZ),
                            1.0f));
                    if (projected.w <= 0.0f) return Float.POSITIVE_INFINITY;
                    var normalizedX = projected.x / projected.w;
                    var normalizedY = projected.y / projected.w;
                    minX = Math.min(minX, normalizedX);
                    minY = Math.min(minY, normalizedY);
                    maxX = Math.max(maxX, normalizedX);
                    maxY = Math.max(maxY, normalizedY);
                }
            }
        }
        return Math.max((maxX - minX) * frame.viewportWidth(), (maxY - minY) * frame.viewportHeight()) * 0.5f;
    }

    private static void createCubeMesh(BufferBuilder builder) {
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

    private static void createLodMesh(BufferBuilder builder, int side, int step) {
        for (int dx = 0; dx < side; dx += step) {
            for (int dz = 0; dz < side; dz += step) {
                builder.addVertex(dx, 0, dz);
                builder.addVertex(dx, 0, dz + step);
                builder.addVertex(dx + step, 0, dz);
                builder.addVertex(dx + step, 0, dz);
                builder.addVertex(dx, 0, dz + step);
                builder.addVertex(dx + step, 0, dz + step);
            }
        }
    }
}
