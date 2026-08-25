package com.xkball.x3dmap.client.render.pip.layers;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.xkball.x3dmap.api.client.render.IMap3dLayer;
import com.xkball.x3dmap.api.client.render.IMap3dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMap3dRenderContext;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.x3dmap.client.b3d.pipeline.X3dMapRenderPipelines;
import com.xkball.x3dmap.client.terrain.TerrainChunkManager;
import com.xkball.x3dmap.client.terrain.file.MapLevel;
import com.xkball.x3dmap.client.terrain.render.MapNodeModel;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.api.client.mixin.IExtendedRenderPass;
import com.xkball.xklibmc.client.b3d.IndirectDrawCommand;
import com.xkball.xklibmc.client.b3d.mesh.CachedMesh;
import com.xkball.xklibmc.utils.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;

@NonNullByDefault
public class TerrainRenderer implements IMap3dLayer {
    
    private static final int MAX_LOD_LEVEL = 4;
    private static final float MIN_SCREEN_SIZE = 256.0f;

    public static final CachedMesh CUBE = new CachedMesh("cube", X3dMapRenderPipelines.WORLD_TERRAIN_PIP, TerrainRenderer::createCubeMesh, true).setCloseOnExit();
//    public static final CachedMesh CHUNK1 = new CachedMesh("lod1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 16, 1), true).setCloseOnExit();
//    public static final CachedMesh REGION1 = new CachedMesh("region1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 512, 1), true).setCloseOnExit();
//    public static final CachedMesh REGION2 = new CachedMesh("region1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 512, 2), true).setCloseOnExit();
//    public static final CachedMesh REGION3 = new CachedMesh("region1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 512, 4), true).setCloseOnExit();
//    public static final CachedMesh REGION4 = new CachedMesh("region1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 512, 8), true).setCloseOnExit();
//    public static final CachedMesh[] LODS = new CachedMesh[]{CHUNK1, REGION1, REGION2, REGION3, REGION4};

    @Override
    public IMap3dRenderCommand prepareRender(IMapFrame frame) {
        return this::render;
    }

    private void render(IMap3dRenderContext context) {
        if (TerrainChunkManager.INSTANCE.compatibleMode) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var mapLevel = TerrainChunkManager.INSTANCE.currentChunkStorage;
        if (mapLevel == null || !mapLevel.getLevel().equals(level.dimension().identifier())) return;
        var frame = context.frame();
        var camera = frame.camera();
        var cameraPosition = new Vector3f(frame.cameraPosition());
        var minNodeY = Math.floorDiv(mapLevel.getMinY(), 512);
        var maxNodeY = Math.floorDiv(mapLevel.getMaxY() - 1, 512);
        var renderNodes = new ArrayList<RenderCandidate>();
        for (var region : mapLevel.getRegions()) {
            for (var nodeY = minNodeY; nodeY <= maxNodeY; nodeY++) {
                var y = nodeY * 512;
                var rootPos = new BlockPos(region.getMinX(), y, region.getMinZ());
                var model = mapLevel.getNodeModelOrLoad(rootPos, MAX_LOD_LEVEL);
                if (model == null || model.isEmpty()) continue;
                this.collectNode(mapLevel, frame, rootPos, model, MAX_LOD_LEVEL, renderNodes);
            }
        }
        if (renderNodes.isEmpty()) return;
        var batchesByLod = new ArrayList<ArrayList<RenderBatch>>(MAX_LOD_LEVEL + 1);
        for (var lodLevel = 0; lodLevel <= MAX_LOD_LEVEL; lodLevel++) {
            batchesByLod.add(new ArrayList<>());
        }
        var batchesByBuffer = new IdentityHashMap<GpuBuffer, RenderBatch>();
        for (var renderNode : renderNodes) {
            var future = mapLevel.getGpuNodeAsync(renderNode.pos(), renderNode.lodLevel());
            if (!isCompleted(future)) continue;
            var gpuModel = future.getNow(null);
            if (gpuModel == null || gpuModel.allocation() == null) continue;
            var blockBuffer = gpuModel.buffer().getGpuBuffer(gpuModel.allocation());
            var batch = batchesByBuffer.get(blockBuffer);
            if (batch == null) {
                batch = new RenderBatch(blockBuffer, renderNode.lodLevel(), new ArrayList<>(), new ArrayList<>());
                batchesByBuffer.put(blockBuffer, batch);
                batchesByLod.get(renderNode.lodLevel()).add(batch);
            }
            batch.nodes().add(new RenderNode(gpuModel.offset(), gpuModel.len()));
        }
        if (batchesByBuffer.isEmpty()) return;
        var batches = new ArrayList<>(batchesByBuffer.values());
        try {
            for (var batch : batches) {
                for (var direction : VanillaUtils.DIRECTIONS) {
                    var commands = new ArrayList<IndirectDrawCommand>(batch.nodes().size());
                    for (var node : batch.nodes()) {
                        commands.add(new IndirectDrawCommand(6, node.len(), direction.get3DDataValue() * 6, 0, node.offset()));
                    }
                    batch.commandBuffers().add(IndirectDrawCommand.buildCommandList(commands));
                }
            }
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
                    for (var lodLevel = 0; lodLevel <= MAX_LOD_LEVEL; lodLevel++) {
                        for (var direction : VanillaUtils.DIRECTIONS) {
                            renderpass.setPipeline(X3dMapRenderPipelines.WORLD_TERRAIN_NEW[lodLevel][direction.get3DDataValue()]);
                            for (var batch : batchesByLod.get(lodLevel)) {
                                IExtendedRenderPass.cast(renderpass).xklib$setSSBO("ABlock", batch.blockBuffer().slice());
                                IExtendedRenderPass.cast(renderpass).xklib$multiDrawElementsIndirect(batch.commandBuffers().get(direction.get3DDataValue()), batch.drawCount());
                            }
                        }
                    }
                }
            } finally {
                RenderSystem.getModelViewStack().popMatrix();
            }
        } finally {
            for (var batch : batches) {
                batch.close();
            }
        }
    }

    private void collectNode(MapLevel mapLevel, IMapFrame frame, BlockPos pos,
                             MapNodeModel model, int lodLevel, ArrayList<RenderCandidate> renderNodes) {
        var bounds = nodeBounds(model);
        if (!frame.isVisible(bounds)) return;
        if (lodLevel == 0 || screenSize(frame, bounds) <= MIN_SCREEN_SIZE) {
            renderNodes.add(new RenderCandidate(pos, lodLevel));
            return;
        }
        var childLodLevel = lodLevel - 1;
        var childSideLength = 1 << (model.depth - 1);
        var childModels = new MapNodeModel[8];
        var pending = false;
        var childIndex = 0;
        var minX = model.x << model.depth;
        var minY = model.y << model.depth;
        var minZ = model.z << model.depth;
        for (var yOffset = 0; yOffset < 2; yOffset++) {
            for (var zOffset = 0; zOffset < 2; zOffset++) {
                for (var xOffset = 0; xOffset < 2; xOffset++) {
                    var childPos = new BlockPos(
                            minX + xOffset * childSideLength,
                            minY + yOffset * childSideLength,
                            minZ + zOffset * childSideLength);
                    var childModel = mapLevel.getNodeModelOrLoad(childPos, childLodLevel);
                    if (childModel == null) {
                        pending = true;
                    } else {
                        childModels[childIndex] = childModel;
                    }
                    childIndex++;
                }
            }
        }
        if (pending) {
            renderNodes.add(new RenderCandidate(pos, lodLevel));
            return;
        }
        childIndex = 0;
        for (var yOffset = 0; yOffset < 2; yOffset++) {
            for (var zOffset = 0; zOffset < 2; zOffset++) {
                for (var xOffset = 0; xOffset < 2; xOffset++) {
                    var childModel = childModels[childIndex++];
                    if (!childModel.isEmpty()) {
                        var childPos = new BlockPos(
                                minX + xOffset * childSideLength,
                                minY + yOffset * childSideLength,
                                minZ + zOffset * childSideLength);
                        this.collectNode(mapLevel, frame, childPos, childModel, childLodLevel, renderNodes);
                    }
                }
            }
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
                    var projected = frame.projectionMatrix().transform(new Vector4f(
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

    private record RenderCandidate(BlockPos pos, int lodLevel) {
    }

    private record RenderNode(int offset, int len) {
    }

    private record RenderBatch(GpuBuffer blockBuffer, int lodLevel, ArrayList<RenderNode> nodes,
                               ArrayList<GpuBuffer> commandBuffers) implements AutoCloseable {

        private int drawCount() {
            return this.nodes.size();
        }

        @Override
        public void close() {
            for (var commandBuffer : this.commandBuffers) {
                commandBuffer.close();
            }
        }
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
