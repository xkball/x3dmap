package com.xkball.x3dmap.client.render.pip;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.api.client.render.Map3dLayerPhase;
import com.xkball.x3dmap.client.b3d.X3dMapPostProcesses;
import com.xkball.x3dmap.client.map.render.Map3dRenderContextImpl;
import com.xkball.x3dmap.client.map.render.MapLayerFrame;
import com.xkball.x3dmap.client.map.render.PreparedMap3dLayer;
import com.xkball.x3dmap.client.map.viewport.MapFrameSnapshot;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.client.b3d.postprocess.XKLibPostProcesses;
import com.xkball.xklibmc.client.b3d.uniform.XKLibUniforms;
import com.xkball.xklibmc.utils.ClientUtils;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@NonNullByDefault
public class WorldTerrainPipRenderer extends OffScreenPIPRenderer<WorldTerrainPipRenderer.WorldTerrainState> {

    private static final Logger LOGGER = LogUtils.getLogger();
    @SuppressWarnings("NotNullFieldNotInitialized")
    public static GpuBufferSlice projBuffer;
    private final ProjectionMatrixBuffer projection = new ProjectionMatrixBuffer("world_terrain_pip_proj");
    private final CameraRenderState cameraRenderState = new CameraRenderState();

    public WorldTerrainPipRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<WorldTerrainState> getRenderStateClass() {
        return WorldTerrainState.class;
    }

    @Override
    protected void renderToTexture(WorldTerrainState renderState, PoseStack ignoredPoseStack) {
        var frame = renderState.frame();
        var projectionMatrix = new Matrix4f(frame.projectionMatrix());
        projBuffer = this.projection.getBuffer(projectionMatrix);
        RenderSystem.backupProjectionMatrix();
        try {
            RenderSystem.setProjectionMatrix(projBuffer, ProjectionType.PERSPECTIVE);
            ClientUtils.getCommandEncoder().clearColorTexture(RenderSystem.outputColorTextureOverride.texture(), 0xff000000);
            ClientUtils.getCommandEncoder().clearDepthTexture(RenderSystem.outputDepthTextureOverride.texture(), 1.0);
            var camera = frame.camera();
            var cameraPosition = new Vector3f(frame.cameraPosition());
            this.cameraRenderState.yRot = camera.yRotation();
            this.cameraRenderState.xRot = camera.xRotation();
            this.cameraRenderState.pos = new Vec3(cameraPosition);
            this.cameraRenderState.blockPos = new BlockPos((int) cameraPosition.x, (int) cameraPosition.y, (int) cameraPosition.z);
            this.cameraRenderState.projectionMatrix = projectionMatrix;
            var hasTerrain = false;
            for (var layer : renderState.layers().threeDimensionalLayers()) {
                if (layer.spec().phase() == Map3dLayerPhase.AFTER_EFFECTS) {
                    continue;
                }
                hasTerrain |= layer.spec().phase() == Map3dLayerPhase.TERRAIN;
                this.renderLayer(layer, frame);
            }
            if (hasTerrain) {
                var cameraDirection = new Vector3f(frame.cameraDirection());
                XKLibUniforms.INVERSE_PROJ_MAT.updateUnsafe(buffer -> {
                    buffer.putMat4f(projectionMatrix.invert(new Matrix4f()));
                    buffer.putMat4f(projectionMatrix);
                    buffer.putVec4(new Vector4f(cameraDirection, 1));
                    buffer.putVec4(new Vector4f(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z, 1));
                });
//            XKLibPostProcesses.SSAO.apply(RenderSystem.outputColorTextureOverride, RenderSystem.outputDepthTextureOverride);
                XKLibPostProcesses.SSR.apply(RenderSystem.outputColorTextureOverride, RenderSystem.outputDepthTextureOverride);
                X3dMapPostProcesses.TERRAIN_DEPTH_EDGE.apply(RenderSystem.outputColorTextureOverride, RenderSystem.outputDepthTextureOverride);
            }
            for (var layer : renderState.layers().threeDimensionalLayers()) {
                if (layer.spec().phase() == Map3dLayerPhase.AFTER_EFFECTS) {
                    this.renderLayer(layer, frame);
                }
            }
        } finally {
            this.endBatch("map viewport");
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private void renderLayer(PreparedMap3dLayer layer, MapFrameSnapshot frame) {
        var poseStack = new PoseStack();
//        poseStack.translate(-renderState.centerPos().getX(), 0, -renderState.centerPos().getZ());
        var context = new Map3dRenderContextImpl(
                frame,
                poseStack,
                this.bufferSource,
                this.cameraRenderState,
                RenderSystem.outputColorTextureOverride,
                RenderSystem.outputDepthTextureOverride
        );
        try {
            layer.command().render(context);
//            ClientUtils.renderAxis(this.bufferSource,poseStack,1000);
        } catch (Exception e) {
            LOGGER.error("Failed to render 3D map layer {}", layer.spec().id(), e);
        } finally {
            this.endBatch(layer.spec().id().toString());
        }
    }

    private void endBatch(String source) {
        try {
            this.bufferSource.endBatch();
        } catch (Exception e) {
            LOGGER.error("Failed to flush 3D map layer buffers for {}", source, e);
        }
    }

    @Override
    protected String getTextureLabel() {
        return "world terrain";
    }

    public MultiBufferSource.BufferSource getBufferSource() {
        return this.bufferSource;
    }

    public record WorldTerrainState(
            MapFrameSnapshot frame,
            MapLayerFrame layers,
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
