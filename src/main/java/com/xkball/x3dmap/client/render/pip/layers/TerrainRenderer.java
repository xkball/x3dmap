package com.xkball.x3dmap.client.render.pip.layers;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.xkball.x3dmap.api.client.render.IMap3dLayer;
import com.xkball.x3dmap.api.client.render.IMap3dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMap3dRenderContext;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.x3dmap.client.b3d.pipeline.X3dMapRenderPipelines;
import com.xkball.x3dmap.client.terrain.TerrainChunkManager;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.api.client.mixin.IExtendedRenderPass;
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
import java.util.OptionalDouble;
import java.util.OptionalInt;

@NonNullByDefault
public class TerrainRenderer implements IMap3dLayer {

    private static final int LOD4_NODE_SIZE = 512;
    private static final int NODE_ENTRY_SIZE = 16;

    public static final CachedMesh CUBE = new CachedMesh("cube", X3dMapRenderPipelines.WORLD_TERRAIN_PIP, TerrainRenderer::createCubeMesh, true).setCloseOnExit();
    public static final CachedMesh CHUNK1 = new CachedMesh("lod1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 16, 1), true).setCloseOnExit();
    public static final CachedMesh REGION1 = new CachedMesh("region1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 512, 1), true).setCloseOnExit();
    public static final CachedMesh REGION2 = new CachedMesh("region1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 512, 2), true).setCloseOnExit();
    public static final CachedMesh REGION3 = new CachedMesh("region1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 512, 4), true).setCloseOnExit();
    public static final CachedMesh REGION4 = new CachedMesh("region1", X3dMapRenderPipelines.WORLD_TERRAIN_PIP_LOD, (b) -> TerrainRenderer.createLodMesh(b, 512, 8), true).setCloseOnExit();
    public static final CachedMesh[] LODS = new CachedMesh[]{CHUNK1, REGION1, REGION2, REGION3, REGION4};

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
        var minNodeY = Math.floorDiv(mapLevel.getMinY(), LOD4_NODE_SIZE);
        var maxNodeY = Math.floorDiv(mapLevel.getMaxY() - 1, LOD4_NODE_SIZE);
        var nodes = new ArrayList<RenderNode>();
        for (var region : mapLevel.getRegions()) {
            for (var nodeY = minNodeY; nodeY <= maxNodeY; nodeY++) {
                var y = nodeY * LOD4_NODE_SIZE;
                if (!frame.isVisible(new AABB(region.getMinX(), y, region.getMinZ(), region.getMinX() + LOD4_NODE_SIZE,
                        y + LOD4_NODE_SIZE, region.getMinZ() + LOD4_NODE_SIZE))) continue;
                var future = mapLevel.getLod4NodeAsync(new BlockPos(region.getMinX(), y, region.getMinZ()));
                if (!future.isDone() || future.isCompletedExceptionally()) continue;
                var model = future.getNow(null);
                if (model == null || model.len() == 0 || model.allocation() == null) continue;
                nodes.add(new RenderNode(model.buffer().getGpuBuffer(model.allocation()).slice(model.allocation().getOffsetFromHeap(), (long) model.len() * NODE_ENTRY_SIZE), model.len()));
            }
        }
        if (nodes.isEmpty()) return;
        RenderSystem.getModelViewStack().pushMatrix();
        try {
            var modelView = RenderSystem.getModelViewStack().mul(context.poseStack().last().pose(), new Matrix4f());
            var transformUBO = RenderSystem.getDynamicUniforms().writeTransform(modelView, new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
            X3dMapRenderPipelines.PHONE_LIGHT.updateUnsafe(b ->
                    b.putVec3(VanillaUtils.dirVec(Mth.clamp(camera.xRotation(), 45, 90), camera.yRotation() + 2))
                            .putVec3(cameraPosition));
//                            if(infoBlock.lod() > 0) continue;
            try (var renderpass = ClientUtils.getCommandEncoder().createRenderPass(
                    () -> "world terrain lod4 rendering", context.colorTarget(), OptionalInt.empty(), context.depthTarget(), OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(renderpass);
                renderpass.setVertexBuffer(0, CUBE.getVertexBuffer());
                renderpass.setIndexBuffer(CUBE.getIndexBuffer(), CUBE.getIndexType());
                for (var direction : VanillaUtils.DIRECTIONS) {
                    renderpass.setPipeline(X3dMapRenderPipelines.WORLD_TERRAIN_NEW[direction.get3DDataValue()]);
                    renderpass.setUniform("DynamicTransforms", transformUBO);
                    for (var node : nodes) {
                        IExtendedRenderPass.cast(renderpass).xklib$setSSBO("ABlock", node.buffer());
                        renderpass.drawIndexed(0, direction.get3DDataValue() * 6, 6, node.len());
                    }
                }
            }
        } finally {
            RenderSystem.getModelViewStack().popMatrix();
        }
    }

    private record RenderNode(GpuBufferSlice buffer, int len) {
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
