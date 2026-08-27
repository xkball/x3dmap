package com.xkball.x3dmap.client.render.ber;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.xkball.x3dmap.ClientConfig;
import com.xkball.x3dmap.block.entity.TerrainProjectorBlockEntity;
import com.xkball.x3dmap.client.render.pip.TerrainProjectorPipRenderer;
import com.xkball.x3dmap.client.terrain.TerrainMapManager;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NonNullByDefault
public final class TerrainProjectorBlockEntityRenderer implements BlockEntityRenderer<TerrainProjectorBlockEntity, TerrainProjectorBlockEntityRenderer.RenderState> {

    public TerrainProjectorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(
            TerrainProjectorBlockEntity blockEntity,
            RenderState state,
            float partialTicks,
            Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.normalizedCenterPos = blockEntity.getNormalizedCenterPos();
        state.modelPositions = blockEntity.getModelPositions();
        state.lodLevel = blockEntity.lodLevel;
        state.yOffset = blockEntity.yOffset;
        if (blockEntity.getLevel() != null) {
            var seaLevel = ClientConfig.getEffectiveDimensionConfig(blockEntity.getLevel().dimension(), blockEntity.getLevel().getSeaLevel())
                    .resolveSeaLevel(blockEntity.getLevel().getSeaLevel());
            state.normalizedSeaLevel = Math.floorDiv(seaLevel, blockEntity.getModelSideLength()) * blockEntity.getModelSideLength();
        }
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.modelPositions.isEmpty()) {
            return;
        }
        var sideLength = 1 << (state.lodLevel + 5);
        var originX = state.normalizedCenterPos.getX() + sideLength * 0.5F;
        var originZ = state.normalizedCenterPos.getZ() + sideLength * 0.5F;
        var cameraPosition = new Vector3f(
                (float) (originX + (camera.pos.x() - state.blockPos.getX() - 0.5) * sideLength),
                (float) (state.normalizedSeaLevel + (camera.pos.y() - state.blockPos.getY() - 1.0 - state.yOffset) * sideLength),
                (float) (originZ + (camera.pos.z() - state.blockPos.getZ() - 0.5) * sideLength)
        );
//        var lightDirection = new Vector3f(0, 0, 1).rotate(camera.orientation);
        var lightDirection = new Vector3f(0, 1 ,0.5f).rotateY((float) Math.toRadians(-camera.yRot)).normalize();
        var projection = RenderSystem.getProjectionMatrixBuffer();
        if (projection == null) {
            return;
        }
        var projectionType = RenderSystem.getProjectionType();
        var modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        poseStack.pushPose();
        poseStack.translate(0.5, 1.0 + state.yOffset, 0.5);
        poseStack.scale(1.0F / sideLength, 1.0F / sideLength, 1.0F / sideLength);
        poseStack.translate(-originX, -state.normalizedSeaLevel, -originZ);
        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.solidMovingBlock(), (pose, _) -> {
            var level = Minecraft.getInstance().level;
            var mapLevel = TerrainMapManager.INSTANCE.getCurrentLevelChunkStorage();
            if (level == null || mapLevel == null || !mapLevel.getLevel().equals(level.dimension().identifier())) {
                return;
            }
            RenderSystem.backupProjectionMatrix();
            try {
                RenderSystem.setProjectionMatrix(projection, projectionType);
                var renderPoseStack = new PoseStack();
                renderPoseStack.mulPose(modelView);
                renderPoseStack.mulPose(pose.pose());
                TerrainProjectorPipRenderer.renderTerrain(
                        mapLevel,
                        state.modelPositions,
                        state.lodLevel,
                        renderPoseStack,
                        cameraPosition,
                        lightDirection,
                        Minecraft.getInstance().getMainRenderTarget().getColorTextureView(),
                        Minecraft.getInstance().getMainRenderTarget().getDepthTextureView()
                );
            } finally {
                RenderSystem.restoreProjectionMatrix();
            }
        });
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(TerrainProjectorBlockEntity blockEntity) {
        var mapLevel = TerrainMapManager.INSTANCE.getCurrentLevelChunkStorage();
        if (blockEntity.getLevel() == null || mapLevel == null
                || !mapLevel.getLevel().equals(blockEntity.getLevel().dimension().identifier())) {
            return new AABB(blockEntity.getBlockPos());
        }
        var sideLength = blockEntity.getModelSideLength();
        var seaLevel = ClientConfig.getEffectiveDimensionConfig(
                blockEntity.getLevel().dimension(),
                blockEntity.getLevel().getSeaLevel()
        ).resolveSeaLevel(blockEntity.getLevel().getSeaLevel());
        var normalizedSeaLevel = Math.floorDiv(seaLevel, sideLength) * sideLength;
        var minModelY = Math.floorDiv(mapLevel.getMinY(), sideLength) * sideLength;
        var maxModelY = (Math.floorDiv(mapLevel.getMaxY() - 1, sideLength) + 1) * sideLength;
        return new AABB(
                blockEntity.getBlockPos().getX() - blockEntity.projectionRadius,
                blockEntity.getBlockPos().getY() + 1.0 + blockEntity.yOffset + (double) (minModelY - normalizedSeaLevel) / sideLength,
                blockEntity.getBlockPos().getZ() - blockEntity.projectionRadius,
                blockEntity.getBlockPos().getX() + blockEntity.projectionRadius + 1,
                blockEntity.getBlockPos().getY() + 1.0 + blockEntity.yOffset + (double) (maxModelY - normalizedSeaLevel) / sideLength,
                blockEntity.getBlockPos().getZ() + blockEntity.projectionRadius + 1
        );
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    public static final class RenderState extends BlockEntityRenderState {

        private BlockPos normalizedCenterPos = BlockPos.ZERO;
        private List<BlockPos> modelPositions = List.of();
        private int lodLevel;
        private float yOffset;
        private int normalizedSeaLevel;
    }
}
