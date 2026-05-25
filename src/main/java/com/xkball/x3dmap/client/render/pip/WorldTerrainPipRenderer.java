package com.xkball.x3dmap.client.render.pip;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.xkball.xklib.api.gui.widget.IGuiWidget;
import com.xkball.xklibmc.utils.ClientUtils;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.x3dmap.api.client.render.PictureInPictureRenderLayer;
import com.xkball.x3dmap.client.render.pip.layers.CameraTargetRenderer;
import com.xkball.x3dmap.client.render.pip.layers.GridRenderer;
import com.xkball.x3dmap.client.render.pip.layers.PlayerOnMapRenderer;
import com.xkball.x3dmap.client.render.pip.layers.TerrainRenderer;
import com.xkball.x3dmap.client.terrain.LevelChunkStorage;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@NonNullByDefault
public class WorldTerrainPipRenderer extends PictureInPictureRenderer<WorldTerrainPipRenderer.WorldTerrainState> {
    
    private static final Set<Supplier<PictureInPictureRenderLayer<WorldTerrainPipRenderer,WorldTerrainState>>> regRenderLayers = new HashSet<>();
    @SuppressWarnings("NotNullFieldNotInitialized")
    public static GpuBufferSlice projBuffer;
    
    private final Map<String, PictureInPictureRenderLayer<WorldTerrainPipRenderer,WorldTerrainState>> renderLayers = new LinkedHashMap<>();
    public final ProjectionMatrixBuffer proj = new ProjectionMatrixBuffer("world_terrain_pip_proj");
    public Matrix4f projMatrix = new Matrix4f();
    public CameraRenderState cameraRenderState = new CameraRenderState();
    
    static {
        regRenderLayers(TerrainRenderer::new);
        regRenderLayers(GridRenderer::new);
        regRenderLayers(PlayerOnMapRenderer::new);
        regRenderLayers( CameraTargetRenderer::new);
    }
    
    public static void regRenderLayers(Supplier<PictureInPictureRenderLayer<WorldTerrainPipRenderer,WorldTerrainState>> renderLayer) {
        regRenderLayers.add(renderLayer);
    }
    
    public WorldTerrainPipRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
        for(var s : regRenderLayers) {
            var layer = s.get();
            this.renderLayers.put(layer.name(), layer);
        }
    }

    @Override
    public Class<WorldTerrainState> getRenderStateClass() {
        return WorldTerrainState.class;
    }
    
    @Override
    protected void renderToTexture(WorldTerrainState renderState, PoseStack poseStack_) {
        var poseStack = new PoseStack();
        poseStack.pushPose();
        var cameraPos = renderState.cameraPos();
        projMatrix = renderState.projMatrix();
        projBuffer = proj.getBuffer(projMatrix);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projBuffer, ProjectionType.PERSPECTIVE);
        ClientUtils.getCommandEncoder().clearColorTexture(RenderSystem.outputColorTextureOverride.texture(),0xff000000);
//        poseStack.translate(-renderState.centerPos().getX(), 0, -renderState.centerPos().getZ());
        this.cameraRenderState.yRot = renderState.yRot;
        this.cameraRenderState.xRot = renderState.xRot;
        this.cameraRenderState.pos = new Vec3(cameraPos);
        this.cameraRenderState.blockPos = new BlockPos((int) cameraPos.x, (int) cameraPos.y, (int) cameraPos.z);
        this.cameraRenderState.projectionMatrix = projMatrix;
        for(var layer : renderState.enabledLayers){
            var renderer = renderLayers.get(layer);
            if(renderer != null){
                renderer.render(this, renderState, poseStack, RenderSystem.outputColorTextureOverride, RenderSystem.outputDepthTextureOverride);
            }
        }
//        ClientUtils.renderAxis(this.bufferSource,poseStack,1000);
        this.bufferSource.endBatch();
        RenderSystem.restoreProjectionMatrix();
        poseStack.popPose();
    }
    
    @Override
    protected String getTextureLabel() {
        return "world terrain";
    }
    
    public MultiBufferSource.BufferSource getBufferSource() {
        return bufferSource;
    }
    
    public static final class WorldTerrainState implements PictureInPictureRenderState {
        private final List<String> enabledLayers;
        private final Vector3f cameraTarget;
        private final BlockPos centerPos;
        private final float fov;
        private final float cameraLength;
        private final float xRot;
        private final float yRot;
        private final int x0;
        private final int x1;
        private final int y0;
        private final int y1;
        private final float scale;
        private final boolean cullNear;
        private final int lodDistance;
        private final boolean minimap;
        private final int minimapHighDetailRangeChunks;
        private final @Nullable ScreenRectangle scissorArea;
        private final @Nullable ScreenRectangle bounds;
        private final Matrix4f projMatrix;
        private final Frustum frustum;
        
        public WorldTerrainState(List<String> enabledLayers,
                                 Vector3f cameraTarget,
                                 BlockPos centerPos,
                                 float fov,
                                 float cameraLength,
                                 float xRot,
                                 float yRot,
                                 int x0,
                                 int x1,
                                 int y0,
                                 int y1,
                                 float scale,
                                 boolean cullNear,
                                 int lodDistance,
                                 boolean minimap,
                                 int minimapHighDetailRangeChunks,
                                 @Nullable ScreenRectangle scissorArea,
                                 @Nullable ScreenRectangle bounds) {
            this.enabledLayers = enabledLayers;
            this.cameraTarget = cameraTarget;
            this.centerPos = centerPos;
            this.fov = fov;
            this.cameraLength = cameraLength;
            this.xRot = xRot;
            this.yRot = yRot;
            this.x0 = x0;
            this.x1 = x1;
            this.y0 = y0;
            this.y1 = y1;
            this.scale = scale;
            this.cullNear = cullNear;
            this.lodDistance = lodDistance;
            this.minimap = minimap;
            this.minimapHighDetailRangeChunks = minimapHighDetailRangeChunks;
            this.scissorArea = scissorArea;
            this.bounds = bounds;
            this.projMatrix = this.calculateProjMatrix(false);
            this.frustum = new Frustum(new Matrix4f(), projMatrix);
        }
        
        public Frustum frustum() {
            return frustum;
        }
        
        public Vector3f dirVec() {
            return VanillaUtils.dirVec(xRot,yRot);
        }
        
        public Vector3f cameraOffset() {
            return dirVec().normalize(cameraLength + 100);
        }
        
        public Vector3f cameraPos() {
            return cameraOffset().add(cameraTarget);
        }
        
        public Matrix4f calculateProjMatrix(boolean revZ) {
            var aspect = (x1 - x0) / ((float) y1 - (float) y0);
            var cameraPos = cameraPos();
            return new Matrix4f().perspective((float) Math.toRadians(fov), aspect, Math.max(1,cameraPos.y/10), Math.max(cameraLength * 3, 16000), revZ)
                    .lookAt(cameraPos.x, cameraPos.y, cameraPos.z,
                            cameraTarget.x, cameraTarget.y, cameraTarget.z,
                            0, 1, 0);
        }
        
        public Vector2f projWorld2Screen(IGuiWidget widget, Vector3f worldPos) {
            var p = this.projMatrix.transform(new Vector4f(worldPos,1f));
            var x = p.x / p.w;
            var y = p.y / p.w;
            return new Vector2f((1 + x) / 2 * widget.getWidth() + widget.getX(), (1 - y) / 2 * widget.getHeight() + widget.getY());
        }
        
        public @Nullable Vector3f projScreen2World(IGuiWidget widget, LevelChunkStorage storage, float screenX, float screenY) {
            if(widget.getWidth() <= 0 || widget.getHeight() <= 0) return null;
            var origin = this.cameraPos();
            var ray = this.screenRay(widget, screenX, screenY);
            var baseDistance = Math.max(this.cameraLength + 100, 256);
            var maxDistance = Math.max(Math.max(this.cameraLength * 2, 8000), baseDistance * 4);
            var step = Math.max(baseDistance / 8, 16);
            var prevDistance = 0.0f;
            var prevDelta = this.rayTerrainDelta(storage, origin);
            for(var distance = step; distance <= maxDistance; distance += step) {
                var delta = this.rayTerrainDelta(storage, this.rayPoint(origin, ray, distance));
                if(prevDelta * delta <= 0) {
                    return this.searchRayTerrainHit(storage, origin, ray, prevDistance, distance);
                }
                prevDistance = distance;
                prevDelta = delta;
            }
            return null;
        }
        
        private Vector3f screenRay(IGuiWidget widget, float screenX, float screenY) {
            var x = (screenX - widget.getX()) / widget.getWidth() * 2 - 1;
            var y = 1 - (screenY - widget.getY()) / widget.getHeight() * 2;
            var invMatrix = this.projMatrix.invert(new Matrix4f());
            var near = invMatrix.transform(new Vector4f(x, y, -1, 1));
            var far = invMatrix.transform(new Vector4f(x, y, 1, 1));
            near.div(near.w);
            far.div(far.w);
            return new Vector3f(far.x, far.y, far.z).sub(near.x, near.y, near.z).normalize();
        }
        
        private @Nullable Vector3f searchRayTerrainHit(LevelChunkStorage storage, Vector3f origin, Vector3f ray, float nearDistance, float farDistance) {
            var low = nearDistance;
            var high = farDistance;
            var lowDelta = this.rayTerrainDelta(storage, this.rayPoint(origin, ray, low));
            for(var i = 0; i < 32; i++) {
                var mid = (low + high) * 0.5f;
                var midDelta = this.rayTerrainDelta(storage, this.rayPoint(origin, ray, mid));
                if(lowDelta * midDelta <= 0) {
                    high = mid;
                }
                else {
                    low = mid;
                    lowDelta = midDelta;
                }
            }
            return this.rayPoint(origin, ray, high);
        }
        
        private Vector3f rayPoint(Vector3f origin, Vector3f ray, float distance) {
            return new Vector3f(ray).mul(distance).add(origin);
        }
        
        private Float rayTerrainDelta(LevelChunkStorage storage, Vector3f point) {
            var x = (int) Math.floor(point.x);
            var z = (int) Math.floor(point.z);
            if(storage.getChunk(new ChunkPos(x >> 4, z >> 4)) == null) return point.y - 64;
            return point.y - storage.getHeight(x, z);
        }
        
        public List<String> enabledLayers() {
            return enabledLayers;
        }
        
        public Vector3f cameraTarget() {
            return cameraTarget;
        }
        
        public BlockPos centerPos() {
            return centerPos;
        }
        
        public float fov() {
            return fov;
        }
        
        public float cameraLength() {
            return cameraLength;
        }
        
        public float xRot() {
            return xRot;
        }
        
        public float yRot() {
            return yRot;
        }
        
        @Override
        public int x0() {
            return x0;
        }
        
        @Override
        public int x1() {
            return x1;
        }
        
        @Override
        public int y0() {
            return y0;
        }
        
        @Override
        public int y1() {
            return y1;
        }
        
        @Override
        public float scale() {
            return scale;
        }
        
        public boolean cullNear() {
            return this.cullNear;
        }
        
        public int lodDistance() {
            return lodDistance;
        }

        public boolean minimap() {
            return minimap;
        }

        public int minimapHighDetailRangeChunks() {
            return minimapHighDetailRangeChunks;
        }
        
        @Override
        public @Nullable ScreenRectangle scissorArea() {
            return scissorArea;
        }
        
        @Override
        public @Nullable ScreenRectangle bounds() {
            return bounds;
        }
        
        public Matrix4f projMatrix() {
            return projMatrix;
        }
        
    }
}
