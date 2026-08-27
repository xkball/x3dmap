package com.xkball.x3dmap.client.render.pip;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.xkball.x3dmap.client.b3d.pipeline.X3dMapRenderPipelines;
import com.xkball.x3dmap.client.map.viewport.MapFrameSnapshot;
import com.xkball.x3dmap.client.render.pip.layers.TerrainRenderer;
import com.xkball.x3dmap.client.terrain.RegionPos;
import com.xkball.x3dmap.client.terrain.TerrainMapManager;
import com.xkball.x3dmap.client.terrain.file.MapLevel;
import com.xkball.x3dmap.client.terrain.render.GpuNodeModel;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.api.client.mixin.IExtendedRenderPass;
import com.xkball.xklibmc.client.b3d.IndirectDrawCommand;
import com.xkball.xklibmc.utils.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

@NonNullByDefault
public final class TerrainProjectorPipRenderer extends OffScreenPIPRenderer<TerrainProjectorPipRenderer.TerrainProjectorState> {

    private final ProjectionMatrixBuffer projection = new ProjectionMatrixBuffer("terrain_projector_pip_proj");

    public TerrainProjectorPipRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<TerrainProjectorState> getRenderStateClass() {
        return TerrainProjectorState.class;
    }

    @Override
    protected void renderToTexture(TerrainProjectorState renderState, PoseStack ignoredPoseStack) {
        RenderSystem.backupProjectionMatrix();
        try {
            RenderSystem.setProjectionMatrix(this.projection.getBuffer(new Matrix4f(renderState.frame().projectionMatrix())), ProjectionType.PERSPECTIVE);
            ClientUtils.getCommandEncoder().clearColorTexture(RenderSystem.outputColorTextureOverride.texture(), 0xFF000000);
            ClientUtils.getCommandEncoder().clearDepthTexture(RenderSystem.outputDepthTextureOverride.texture(), 1.0);
            this.renderPreview(renderState);
        } finally {
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private void renderPreview(TerrainProjectorState renderState) {
        var level = Minecraft.getInstance().level;
        var mapLevel = TerrainMapManager.INSTANCE.getCurrentLevelChunkStorage();
        if (level == null || mapLevel == null || !mapLevel.getLevel().equals(level.dimension().identifier())) {
            return;
        }
        var sideLength = 1 << (renderState.lodLevel() + 5);
        var originX = renderState.normalizedCenterPos().getX() + sideLength * 0.5F;
        var originZ = renderState.normalizedCenterPos().getZ() + sideLength * 0.5F;
        var poseStack = new PoseStack();
        poseStack.mulPose(renderState.frame().viewMatrix());
        poseStack.translate(-originX, 0, -originZ);
        renderTerrain(
                mapLevel,
                renderState.modelPositions(),
                renderState.lodLevel(),
                poseStack,
                new Vector3f(renderState.frame().cameraPosition()).add(originX, 0, originZ),
                VanillaUtils.dirVec(
                        Mth.clamp(renderState.frame().camera().xRotation(), 75, 90),
                        -renderState.frame().camera().yRotation() + 2
                ),
                RenderSystem.outputColorTextureOverride,
                RenderSystem.outputDepthTextureOverride
        );
    }

    public static void renderTerrain(
            MapLevel mapLevel,
            List<BlockPos> modelPositions,
            int lodLevel,
            PoseStack poseStack,
            Vector3fc cameraPosition,
            Vector3fc lightDirection,
            GpuTextureView colorTarget,
            GpuTextureView depthTarget
    ) {
        var renderNodes = collectNodes(mapLevel, modelPositions, lodLevel);
        if (renderNodes.isEmpty()) {
            return;
        }
        var batchesByBuffer = new IdentityHashMap<GpuBuffer, RenderBatchBuilder>();
        for (var renderNode : renderNodes) {
            var allocation = renderNode.allocation();
            if (allocation == null) {
                continue;
            }
            var blockBuffer = renderNode.buffer().getGpuBuffer(allocation);
            batchesByBuffer.computeIfAbsent(blockBuffer, _ -> new RenderBatchBuilder()).submit(renderNode);
        }
        if (batchesByBuffer.isEmpty()) {
            return;
        }
        batchesByBuffer.forEach((_, builder) -> builder.build());
        try {
            var transform = RenderSystem.getDynamicUniforms().writeTransform(poseStack.last().pose(), new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
            X3dMapRenderPipelines.PHONE_LIGHT.updateUnsafe(buffer -> buffer
                    .putVec3(lightDirection)
                    .putVec3(cameraPosition));
            try (var renderPass = ClientUtils.getCommandEncoder().createRenderPass(
                    () -> "terrain projector rendering",
                    colorTarget,
                    OptionalInt.empty(),
                    depthTarget,
                    OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setVertexBuffer(0, TerrainRenderer.CUBE.getVertexBuffer());
                renderPass.setIndexBuffer(TerrainRenderer.CUBE.getIndexBuffer(), TerrainRenderer.CUBE.getIndexType());
                renderPass.setUniform("DynamicTransforms", transform);
                for (var entry : batchesByBuffer.entrySet()) {
                    renderPass.setPipeline(X3dMapRenderPipelines.WORLD_TERRAIN_NEW[lodLevel]);
                    IExtendedRenderPass.cast(renderPass).xklib$setSSBO("ABlock", entry.getKey().slice());
                    IExtendedRenderPass.cast(renderPass).xklib$setSSBO("CMD", Objects.requireNonNull(entry.getValue().commandBuffer).slice());
                    IExtendedRenderPass.cast(renderPass).xklib$multiDrawElementsIndirect(entry.getValue().commandBuffer, entry.getValue().commandCount);
                }
            }
        } finally {
            batchesByBuffer.forEach((_, builder) -> builder.close());
        }
    }

    private static List<GpuNodeModel> collectNodes(MapLevel mapLevel, List<BlockPos> modelPositions, int lodLevel) {
        var sideLength = 1 << (lodLevel + 5);
        var minY = Math.floorDiv(mapLevel.getMinY(), sideLength) * sideLength;
        var maxY = Math.floorDiv(mapLevel.getMaxY() - 1, sideLength) * sideLength;
        return modelPositions.parallelStream()
                .filter(pos -> mapLevel.getRegions().contains(RegionPos.ofBlock(pos)))
                .flatMap(pos -> {
                    var result = new ArrayList<GpuNodeModel>();
                    for (var y = minY; y <= maxY; y += sideLength) {
                        var modelPos = new BlockPos(pos.getX(), y, pos.getZ());
                        var model = mapLevel.getNodeModelOrLoad(modelPos, lodLevel);
                        if (model == null || model.isEmpty()) {
                            continue;
                        }
                        var gpuModel = mapLevel.getGpuNodeAsync(modelPos, lodLevel).getNow(null);
                        if (gpuModel != null && gpuModel.allocation() != null) {
                            result.add(gpuModel);
                        }
                    }
                    return result.stream();
                })
                .toList();
    }

    @Override
    protected String getTextureLabel() {
        return "terrain projector";
    }

    private static final class RenderBatchBuilder implements AutoCloseable {

        private final List<IndirectDrawCommand> commands = new ArrayList<>();
        private @Nullable GpuBuffer commandBuffer;
        private int commandCount;

        private void submit(GpuNodeModel model) {
            for (var face = 0; face < 6; face++) {
                this.commands.add(new IndirectDrawCommand(6, model.len(), face * 6, 0, this.commandCount, face, model.offset()));
                this.commandCount++;
            }
        }

        private void build() {
            this.commandBuffer = IndirectDrawCommand.buildCommandList(this.commands);
        }

        @Override
        public void close() {
            if (this.commandBuffer != null) {
                this.commandBuffer.close();
            }
        }
    }

    public record TerrainProjectorState(
            MapFrameSnapshot frame,
            BlockPos normalizedCenterPos,
            List<BlockPos> modelPositions,
            int lodLevel,
            int x0,
            int x1,
            int y0,
            int y1,
            float scale,
            @Nullable ScreenRectangle scissorArea,
            @Nullable ScreenRectangle bounds
    ) implements PictureInPictureRenderState {
    }
}
