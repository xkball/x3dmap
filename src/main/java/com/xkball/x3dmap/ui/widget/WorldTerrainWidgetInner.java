package com.xkball.x3dmap.ui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import com.xkball.x3dmap.api.client.gui.input.IMapInputContext;
import com.xkball.x3dmap.api.client.gui.input.MapInputEvent;
import com.xkball.x3dmap.api.client.gui.input.MapInputResult;
import com.xkball.x3dmap.api.client.render.IMapLayerHost;
import com.xkball.x3dmap.api.client.render.Map2dLayerPhase;
import com.xkball.x3dmap.api.client.render.MapViewportPresets;
import com.xkball.x3dmap.api.client.storage.IMapDataHandle;
import com.xkball.x3dmap.api.client.viewport.IMapCamera;
import com.xkball.x3dmap.api.client.viewport.IMapProjection;
import com.xkball.x3dmap.api.client.viewport.IMapViewport;
import com.xkball.x3dmap.api.client.viewport.MapCameraState;
import com.xkball.x3dmap.api.client.viewport.MapRay;
import com.xkball.x3dmap.api.client.viewport.MapViewportSpec;
import com.xkball.x3dmap.client.map.gui.MapScreenSession;
import com.xkball.x3dmap.client.map.render.Map2dLayerRenderer;
import com.xkball.x3dmap.client.map.render.Map2dRenderContextImpl;
import com.xkball.x3dmap.client.map.render.MapLayerHostImpl;
import com.xkball.x3dmap.client.map.runtime.X3dMapRuntimeImpl;
import com.xkball.x3dmap.client.map.uistate.WorldMapUiStateStorage;
import com.xkball.x3dmap.client.map.viewport.MapCameraImpl;
import com.xkball.x3dmap.client.map.viewport.MapFrameSnapshot;
import com.xkball.x3dmap.client.map.viewport.MapInputContextImpl;
import com.xkball.x3dmap.client.render.pip.WorldTerrainPipRenderer;
import com.xkball.x3dmap.client.terrain.TerrainChunkManager;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklib.api.gui.input.IKeyEvent;
import com.xkball.xklib.api.gui.input.IMouseButtonEvent;
import com.xkball.xklib.ui.layout.BooleanLayoutVariable;
import com.xkball.xklib.ui.layout.IntLayoutVariable;
import com.xkball.xklib.ui.render.IGUIGraphics;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklib.ui.widget.container.AbsoluteContainer;
import com.xkball.xklib.ui.widget.container.ContainerWidget;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.ui.XKLibBaseScreen;
import com.xkball.xklibmc.x3d.backend.b3d.B3dGuiGraphics;
import dev.vfyjxf.taffy.geometry.TaffySize;
import dev.vfyjxf.taffy.style.TaffyDimension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.joml.Matrix2f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@NonNullByDefault
public class WorldTerrainWidgetInner extends ContainerWidget implements IMapViewport, IMapProjection {
    private static final String KEY_CAM_XROT = "cam_xrot";
    private static final String KEY_CAM_YROT = "cam_yrot";
    private static final String KEY_CAM_FOV = "cam_fov";
    private static final String KEY_CAM_CAMERA_LENGTH = "cam_camera_length";
    private static final Identifier TERRAIN_LAYER = VanillaUtils.modRL("terrain");
    private static final Identifier GRID_LAYER = VanillaUtils.modRL("grid");
    private static final Identifier PLAYER_LAYER = VanillaUtils.modRL("player");
    private static final Identifier PLAYER_HEADS_LAYER = VanillaUtils.modRL("player_heads");
    private static final Identifier CAMERA_TARGET_LAYER = VanillaUtils.modRL("camera_target");
    private static final Identifier COMPASS_LAYER = VanillaUtils.modRL("compass");

    private final Vector3f cameraTarget = new Vector3f();
    private BlockPos centerPos = BlockPos.ZERO;
    private float xRot = 89.0f;
    private float cameraLength = 0;
    private float yRot = 0.0f;
    private boolean rotating;
    private float fov = 60;
    private WorldTerrainPipRenderer.@Nullable WorldTerrainState lastState;
    private @Nullable MapScreenSession screenSession;
    private @Nullable IMapDataHandle<WorldMapUiStateStorage> uiState;
    private final AbsoluteContainer extensionOverlay = new AbsoluteContainer();
    private final Map<String, Supplier<Widget>> extensionOverlayProviders = new LinkedHashMap<>();
    private final Set<Integer> pressedMovementKeys = new HashSet<>();
    private @Nullable Vector3f dragGrabbedWorldPos;
    private final X3dMapRuntimeImpl runtime;
    private final ResourceKey<Level> dimension;
    private final Identifier preset;
    private final int minimapHighDetailRange;
    private final MapCameraImpl mapCamera;
    private final MapLayerHostImpl layerHost;
    private boolean invalidated = true;
    private boolean closed;

    private final BooleanLayoutVariable terrain;
    private final BooleanLayoutVariable grid;
    private final BooleanLayoutVariable player;
    private final BooleanLayoutVariable cameraTarget_;
    private final BooleanLayoutVariable compass;
    private final BooleanLayoutVariable depress_sphere;
    private final BooleanLayoutVariable debug;
    private final IntLayoutVariable yMode;
    private final IntLayoutVariable fixY;
    private final IntLayoutVariable lodDistance;

    public WorldTerrainWidgetInner(BooleanLayoutVariable terrain, BooleanLayoutVariable grid, BooleanLayoutVariable player, BooleanLayoutVariable cameraTarget, BooleanLayoutVariable compass, BooleanLayoutVariable depress_sphere, BooleanLayoutVariable debug, IntLayoutVariable yMode, IntLayoutVariable fixY, IntLayoutVariable lodDistance) {
        this(
                terrain,
                grid,
                player,
                cameraTarget,
                compass,
                depress_sphere,
                debug,
                yMode,
                fixY,
                lodDistance,
                TerrainChunkManager.INSTANCE.mapPluginRegistry.runtime(),
                currentDimension(),
                MapViewportPresets.WORLD_MAP,
                0
        );
    }

    private WorldTerrainWidgetInner(BooleanLayoutVariable terrain, BooleanLayoutVariable grid, BooleanLayoutVariable player, BooleanLayoutVariable cameraTarget, BooleanLayoutVariable compass, BooleanLayoutVariable depress_sphere, BooleanLayoutVariable debug, IntLayoutVariable yMode, IntLayoutVariable fixY, IntLayoutVariable lodDistance, X3dMapRuntimeImpl runtime, ResourceKey<Level> dimension, Identifier preset, int minimapHighDetailRange) {
        this.terrain = terrain;
        this.grid = grid;
        this.player = player;
        this.cameraTarget_ = cameraTarget;
        this.compass = compass;
        this.depress_sphere = depress_sphere;
        this.debug = debug;
        this.yMode = yMode;
        this.fixY = fixY;
        this.lodDistance = lodDistance;
        this.runtime = runtime;
        this.dimension = dimension;
        this.preset = preset;
        this.minimapHighDetailRange = minimapHighDetailRange;
        this.initCamera();
        this.mapCamera = new MapCameraImpl(this::cameraState, this::applyCameraState, this::invalidate);
        this.layerHost = new MapLayerHostImpl(runtime, this);
        this.layerHost.addRegisteredLayers(runtime.layerRegistry());
        this.setOverflow(false);
        this.extensionOverlay.inlineStyle("size: 100% 100%;");
        this.addChild(this.extensionOverlay);
        this.fixY.addCallback(_ -> this.setCameraY());
        this.yMode.addCallback(_ -> this.setCameraY());
        this.runtime.viewportManagerImpl().track(this);
    }

    public static WorldTerrainWidgetInner createStandalone(X3dMapRuntimeImpl runtime, MapViewportSpec spec) {
        var level = Minecraft.getInstance().level;
        var fixY = new IntLayoutVariable(level == null ? 64 : level.getSeaLevel());
        var viewport = new WorldTerrainWidgetInner(
                new BooleanLayoutVariable(true),
                new BooleanLayoutVariable(true),
                new BooleanLayoutVariable(true),
                new BooleanLayoutVariable(false),
                new BooleanLayoutVariable(true),
                new BooleanLayoutVariable(spec.cullNear()),
                new BooleanLayoutVariable(false),
                new IntLayoutVariable(1),
                fixY,
                new IntLayoutVariable(spec.lodDistance()),
                runtime,
                spec.dimension(),
                spec.preset(),
                spec.minimapHighDetailRange()
        );
        viewport.applyCameraState(spec.initialCamera());
        return viewport;
    }

    public void setScreenSession(MapScreenSession screenSession) {
        this.screenSession = screenSession;
    }

    public void setUiStateHandle(@Nullable IMapDataHandle<WorldMapUiStateStorage> uiState) {
        this.uiState = uiState;
        if (uiState == null) {
            return;
        }
        var state = uiState.value();
        this.xRot = state.getFloat(KEY_CAM_XROT, this.xRot);
        this.yRot = state.getFloat(KEY_CAM_YROT, this.yRot);
        this.fov = state.getFloat(KEY_CAM_FOV, this.fov);
        this.cameraLength = state.getFloat(KEY_CAM_CAMERA_LENGTH, this.cameraLength);
    }

    public void saveUiState() {
        if (this.uiState == null) {
            return;
        }
        var state = this.uiState.value();
        state.setFloat(KEY_CAM_XROT, this.xRot);
        state.setFloat(KEY_CAM_YROT, this.yRot);
        state.setFloat(KEY_CAM_FOV, this.fov);
        state.setFloat(KEY_CAM_CAMERA_LENGTH, this.cameraLength);
    }

    public void setExtensionOverlayProvider(String extensionId, Supplier<Widget> provider) {
        this.extensionOverlayProviders.put(extensionId, provider);
        this.refreshExtensionOverlays();
    }

    public void refreshExtensionOverlays() {
        this.extensionOverlay.clearChildren();
        for (var provider : this.extensionOverlayProviders.values()) {
            this.extensionOverlay.addChild(provider.get());
        }
    }

    private void initCamera() {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return;
        var player = mc.player;
        if (player == null) return;
        var cam = mc.gameRenderer.getMainCamera();
        yRot = cam.yRot();
        centerPos = player.blockPosition();
        centerPos = centerPos.atY(level.getMinY());
        cameraTarget.set(centerPos.getX(), 0, centerPos.getZ());
        this.setCameraY();
    }

    public void reLocateCamera() {
        if (this.mapCamera.externallyControlled()) {
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        this.cameraTarget.x = player.blockPosition().getX();
        this.cameraTarget.z = player.blockPosition().getZ();
        this.invalidate();
    }

    @Override
    public boolean mouseDragged(IMouseButtonEvent event, double dx, double dy) {
        if (!this.enabled || !this.visible()) {
            return false;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public void resize(float offsetX, float offsetY) {
        super.resize(offsetX, offsetY);
        this.invalidate();
        for (var overlay : this.extensionOverlay.getChildren()) {
            overlay.setStyle(s -> s.size = new TaffySize<>(TaffyDimension.length(this.getWidth()), TaffyDimension.length(this.getHeight())));
        }
    }

    public void calculateNewPipState() {
        if (width == 0 || height == 0) {
            lastState = null;
            return;
        }
        this.layerHost.setVisible(TERRAIN_LAYER, this.terrain.get());
        this.layerHost.setVisible(GRID_LAYER, this.grid.get());
        this.layerHost.setVisible(PLAYER_LAYER, this.player.get());
        this.layerHost.setVisible(PLAYER_HEADS_LAYER, this.player.get());
        this.layerHost.setVisible(CAMERA_TARGET_LAYER, this.cameraTarget_.get());
        this.layerHost.setVisible(COMPASS_LAYER, this.compass.get());
        var frame = new MapFrameSnapshot(
                this.dimension,
                this.preset,
                this.cameraState(),
                this.x,
                this.y,
                this.width,
                this.height,
                this.depress_sphere.get(),
                this.lodDistance.get(),
                this.minimapHighDetailRange,
                this.centerPos.getY(),
                TerrainChunkManager.INSTANCE.getCurrentLevelChunkStorage()
        );
        var layers = this.layerHost.prepare(frame);
        var scaleX = XKLibBaseScreen.tryGetScaleX();
        var scaleY = XKLibBaseScreen.tryGetScaleY();
        lastState = new WorldTerrainPipRenderer.WorldTerrainState(
                frame,
                layers,
                (int) (x / scaleX),
                (int) ((x + width) / scaleX),
                (int) (y / scaleY),
                (int) ((y + height) / scaleY),
                1.0f,
                null,
                new ScreenRectangle((int) (x / scaleX), (int) (y / scaleY), (int) (width / scaleX), (int) (height / scaleY))
        );
        this.invalidated = false;
    }

    @Override
    public void doRender(IGUIGraphics graphics, int mouseX, int mouseY, float a) {
        this.tick();
        this.calculateNewPipState();
        if (graphics instanceof B3dGuiGraphics b3dGuiGraphics && lastState != null) {
            var inner = b3dGuiGraphics.getInner();
            inner.submitPictureInPictureRenderState(lastState);
        }
        if (this.lastState != null) {
            var context = new Map2dRenderContextImpl(this.lastState.frame(), graphics);
            Map2dLayerRenderer.render(this.lastState.layers(), Map2dLayerPhase.CONTENT, context);
            if (debug.get()) {
                var y_ = y;
                graphics.drawString("fov: " + fov, x, y_, -1);
                y_ += 10;
                graphics.drawString("xRot: " + xRot, x, y_, -1);
                y_ += 10;
                graphics.drawString("yRot: " + yRot, x, y_, -1);
                y_ += 10;
                graphics.drawString("focus: " + this.isPrimaryFocused(), x, y_, -1);
                y_ += 10;
                graphics.drawString("queue: " + TerrainChunkManager.INSTANCE.taskQueue.taskCount(), x, y_, -1);
                y_ += 10;
                graphics.drawString("memAlloc: " + VanillaUtils.memSize(TerrainChunkManager.INSTANCE.getMemAlloc()), x, y_, -1);
                y_ += 10;
//                graphics.drawString("memUsed: " + VanillaUtils.memSize(TerrainChunkManager.INSTANCE.getMemUsed()),x,y_,-1);
                graphics.drawString("length: " + cameraLength, x, y_, -1);
                y_ += 10;
                graphics.drawString("camTar: " + vec3fToString(cameraTarget), x, y_, -1);
                y_ += 10;
                graphics.drawString("camPos: " + vec3fToString(dirVec().normalize(cameraLength + 100).add(cameraTarget)), x, y_, -1);
                y_ += 10;
            }
        }
        super.doRender(graphics, mouseX, mouseY, a);
        if (this.lastState != null) {
            var context = new Map2dRenderContextImpl(this.lastState.frame(), graphics);
            Map2dLayerRenderer.render(this.lastState.layers(), Map2dLayerPhase.FOREGROUND, context);
        }
    }

    public @Nullable Vector3f projScreen2World(double screenX, double screenY) {
        return this.screenToTerrain(screenX, screenY);
    }

    public @Nullable Vector3f projScreen2World(float screenX, float screenY) {
        return this.screenToTerrain(screenX, screenY);
    }

    public @Nullable Vector2f projWorld2Screen(Vector3f worldPos) {
        return this.worldToScreen(worldPos);
    }

    @Override
    public boolean mouseMoved(double mouseX, double mouseY) {
        if (this.dispatchMapEvent(new MapInputEvent.MouseMoved(mouseX, mouseY))) {
            return true;
        }
        return super.mouseMoved(mouseX, mouseY);
    }

    private String vec3fToString(Vector3f vec) {
        return String.format("( %.2f, %.2f, %.2f )", vec.x(), vec.y(), vec.z());
    }

    private Vector3f dirVec() {
        return VanillaUtils.dirVec(xRot, yRot);
    }

    @Override
    protected boolean onMouseClicked(IMouseButtonEvent event, boolean doubleClick) {
        if (this.dispatchMapEvent(new MapInputEvent.MouseClicked(event, doubleClick))) {
            return true;
        }
        if (this.mapCamera.externallyControlled()) {
            return false;
        }
        if (event.button() == 2) {
            rotating = true;
        }
        if (event.button() == 1) {
            var worldPos = this.projScreen2World((float) event.x(), (float) event.y());
            this.dragGrabbedWorldPos = worldPos != null ? new Vector3f(worldPos) : null;
        }
        return true;
    }

    @Override
    protected boolean onMouseReleased(IMouseButtonEvent event) {
        if (this.dispatchMapEvent(new MapInputEvent.MouseReleased(event))) {
            return true;
        }
        if (this.mapCamera.externallyControlled()) {
            this.rotating = false;
            this.dragGrabbedWorldPos = null;
            return false;
        }
        if (event.button() == 2) {
            rotating = false;
            return true;
        }
        if (event.button() == 1) {
            this.dragGrabbedWorldPos = null;
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseDragged(IMouseButtonEvent event, double dx, double dy) {
        if (this.dispatchMapEvent(new MapInputEvent.MouseDragged(event, dx, dy))) {
            return true;
        }
        if (this.mapCamera.externallyControlled()) {
            return false;
        }
        if (event.button() == 2) {
            if (!rotating) {
                return false;
            }
            float sens = 0.25f * Math.max(0.4f, fov / 100);
            xRot = xRot + (float) dy * sens;
            xRot = Math.clamp(xRot, -89.9f, 89.9f);
            yRot = yRot - (float) dx * sens;
            yRot = (yRot + 360) % 360;
            this.setCameraY();
            return true;
        }
        if (event.button() == 1) {
            if (this.dragGrabbedWorldPos != null) {
                var cameraPos = this.dirVec().normalize(this.cameraLength + 100).add(this.cameraTarget);
                var toW = this.dragGrabbedWorldPos.sub(cameraPos, new Vector3f());
                float dist = Math.abs(toW.dot(this.dirVec()));
                if (dist < 1f) {
                    dist = 1f;
                }
                if (this.height <= 0) {
                    return true;
                }
                float worldPerPixel = (float) (2 * dist * Math.tan(Math.toRadians(this.fov / 2)) / this.height);
                float yawRad = (float) Math.toRadians(this.yRot);
                float rightX = (float) Math.cos(yawRad);
                float rightZ = (float) -Math.sin(yawRad);
                float screenDownX = (float) Math.sin(yawRad);
                float screenDownZ = (float) Math.cos(yawRad);
                float camDX = (float) (-(dx * rightX + dy * screenDownX) * worldPerPixel);
                float camDZ = (float) (-(dx * rightZ + dy * screenDownZ) * worldPerPixel);
                this.cameraTarget.add(camDX, 0, camDZ);
                this.setCameraY();
            } else {
                var speed = 1 + (cameraLength / 100);
                this.moveCamera((float) (-dx / 100) * speed, (float) (-dy / 100) * speed);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (this.dispatchMapEvent(new MapInputEvent.MouseScrolled(x, y, scrollX, scrollY))) {
            return true;
        }
        if (this.mapCamera.externallyControlled()) {
            return false;
        }
        if (fov > 90 - 1e-6) {
            cameraLength -= (float) (scrollY * Math.log10(cameraLength + 10f));
            cameraLength = Math.max(cameraLength, 0);
        }
        if (cameraLength < 1e-6) {
            fov = (float) Math.clamp(fov - scrollY, 5, 90);
        }

        return true;
    }

    @Override
    protected boolean onKeyPressed(IKeyEvent event) {
        if (this.dispatchMapEvent(new MapInputEvent.KeyPressed(event))) {
            return true;
        }
        if (this.mapCamera.externallyControlled()) {
            return false;
        }
        int key = event.key();
        if (key == InputConstants.KEY_W || key == InputConstants.KEY_A
                || key == InputConstants.KEY_S || key == InputConstants.KEY_D
                || key == InputConstants.KEY_Q || key == InputConstants.KEY_E) {
            this.pressedMovementKeys.add(key);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onKeyReleased(IKeyEvent event) {
        if (this.dispatchMapEvent(new MapInputEvent.KeyReleased(event))) {
            return true;
        }
        int key = event.key();
        if (key == InputConstants.KEY_W || key == InputConstants.KEY_A
                || key == InputConstants.KEY_S || key == InputConstants.KEY_D
                || key == InputConstants.KEY_Q || key == InputConstants.KEY_E) {
            this.pressedMovementKeys.remove(key);
            return true;
        }
        return false;
    }

    public void tick() {
        this.layerHost.tick();
        if (this.screenSession != null) {
            this.screenSession.tick();
        }
        if (this.mapCamera.externallyControlled()) {
            this.pressedMovementKeys.clear();
            return;
        }
        if (this.pressedMovementKeys.isEmpty()) {
            return;
        }
        float dx = 0;
        float dz = 0;
        if (this.pressedMovementKeys.contains(InputConstants.KEY_W)) {
            dz -= 1;
        }
        if (this.pressedMovementKeys.contains(InputConstants.KEY_S)) {
            dz += 1;
        }
        if (this.pressedMovementKeys.contains(InputConstants.KEY_A)) {
            dx -= 1;
        }
        if (this.pressedMovementKeys.contains(InputConstants.KEY_D)) {
            dx += 1;
        }
        if (dx != 0 || dz != 0) {
            this.moveCamera(dx, dz);
        }
        if (this.pressedMovementKeys.contains(InputConstants.KEY_Q)) {
            this.yRot += 0.5f;
            this.yRot = (this.yRot + 360) % 360;
        }
        if (this.pressedMovementKeys.contains(InputConstants.KEY_E)) {
            this.yRot -= 0.5f;
            this.yRot = (this.yRot + 360) % 360;
        }
    }

    private void moveCamera(float dx, float dz) {
        float speed = fov / 120 * (1 + cameraLength / 100);
        var dir = new Vector2f(dx, dz).mul(speed);
        dir.mul(new Matrix2f().rotate((float) Math.toRadians(-yRot)));
        cameraTarget.add(dir.x, 0, dir.y);
        this.setCameraY();
    }

    private void setCameraY() {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            if (yMode.get() == 0) {
                cameraTarget.y = level.getSeaLevel();
                var storage = TerrainChunkManager.INSTANCE.getCurrentLevelChunkStorage();
                if (storage != null) {
                    var h = storage.getHeight((int) cameraTarget.x, (int) cameraTarget.z);
                    if (h != level.getMinY()) {
                        cameraTarget.y = h;
                    }
                }
            } else {
                cameraTarget.y = fixY.get();
            }
        }
    }


    @Override
    public void onFocusChanged(boolean focused) {
        if (!focused) this.rotating = false;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    private boolean dispatchMapEvent(MapInputEvent event) {
        IMapInputContext context = new MapInputContextImpl(this, event);
        var layerResult = this.layerHost.handle(event, context);
        if (layerResult != MapInputResult.PASS) {
            return true;
        }
        return this.screenSession != null && this.screenSession.handle(event);
    }

    @Override
    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    @Override
    public Identifier preset() {
        return this.preset;
    }

    @Override
    public Widget widget() {
        return this;
    }

    @Override
    public IMapCamera camera() {
        return this.mapCamera;
    }

    @Override
    public IMapProjection projection() {
        return this;
    }

    @Override
    public IMapLayerHost layers() {
        return this.layerHost;
    }

    @Override
    public void invalidate() {
        this.invalidated = true;
    }

    @Override
    public @Nullable Vector2f worldToScreen(Vector3fc worldPosition) {
        return this.lastState == null ? null : this.lastState.frame().worldToScreen(worldPosition);
    }

    @Override
    public @Nullable MapRay screenRay(double screenX, double screenY) {
        return this.lastState == null ? null : this.lastState.frame().screenRay(screenX, screenY);
    }

    @Override
    public @Nullable Vector3f screenToTerrain(double screenX, double screenY) {
        return this.lastState == null ? null : this.lastState.frame().screenToTerrain(screenX, screenY);
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.layerHost.close();
        this.mapCamera.close();
        this.pressedMovementKeys.clear();
        this.screenSession = null;
        this.lastState = null;
        this.runtime.viewportManagerImpl().release(this);
    }

    private MapCameraState cameraState() {
        return new MapCameraState(
                this.cameraTarget.x,
                this.cameraTarget.y,
                this.cameraTarget.z,
                this.xRot,
                this.yRot,
                this.cameraLength,
                this.fov
        );
    }

    private void applyCameraState(MapCameraState state) {
        this.cameraTarget.set(state.targetX(), state.targetY(), state.targetZ());
        this.xRot = Math.clamp(state.xRotation(), -89.9f, 89.9f);
        this.yRot = (state.yRotation() % 360 + 360) % 360;
        this.cameraLength = Math.max(0, state.distance());
        this.fov = Math.clamp(state.fieldOfView(), 1, 179);
    }

    private static ResourceKey<Level> currentDimension() {
        var level = Minecraft.getInstance().level;
        return level == null ? Level.OVERWORLD : level.dimension();
    }
}
