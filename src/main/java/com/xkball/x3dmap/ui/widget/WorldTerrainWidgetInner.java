package com.xkball.x3dmap.ui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import com.xkball.x3dmap.api.client.gui.input.MapInputEvent;
import com.xkball.x3dmap.api.client.render.MapRenderTarget;
import com.xkball.x3dmap.api.client.storage.IMapDataHandle;
import com.xkball.x3dmap.client.map.gui.MapScreenSession;
import com.xkball.x3dmap.client.map.minimap.CompassRenderer;
import com.xkball.x3dmap.client.map.uistate.WorldMapUiStateStorage;
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
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix2f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@NonNullByDefault
public class WorldTerrainWidgetInner extends ContainerWidget {
    private static final String KEY_CAM_XROT = "cam_xrot";
    private static final String KEY_CAM_YROT = "cam_yrot";
    private static final String KEY_CAM_FOV = "cam_fov";
    private static final String KEY_CAM_CAMERA_LENGTH = "cam_camera_length";
    
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
        this.initCamera();
        this.setOverflow(false);
        this.extensionOverlay.inlineStyle("size: 100% 100%;");
        this.addChild(this.extensionOverlay);
        this.fixY.addCallback(_ -> this.setCameraY());
        this.yMode.addCallback(_ -> this.setCameraY());
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
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        this.cameraTarget.x = player.blockPosition().getX();
        this.cameraTarget.z = player.blockPosition().getZ();
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
        for (var overlay : this.extensionOverlay.getChildren()) {
            overlay.setStyle(s -> s.size = new TaffySize<>(TaffyDimension.length(this.getWidth()), TaffyDimension.length(this.getHeight())));
        }
    }
    
    public void calculateNewPipState() {
        if (width == 0 && height == 0) {
            lastState = null;
            return;
        }
        var list = new ArrayList<Identifier>();
        for (var id : TerrainChunkManager.INSTANCE.mapPluginRegistry.layerRegistry().layers(MapRenderTarget.WORLD_MAP)) {
            var enabled = switch (id.getPath()) {
                case "terrain" -> terrain.get();
                case "grid" -> grid.get();
                case "player" -> player.get();
                case "camera_target" -> cameraTarget_.get();
                default -> true;
            };
            if (enabled) {
                list.add(id);
            }
        }
        
        var scaleX = XKLibBaseScreen.tryGetScaleX();
        var scaleY = XKLibBaseScreen.tryGetScaleY();
        lastState = new WorldTerrainPipRenderer.WorldTerrainState(
                list,
                new Vector3f(cameraTarget),
                centerPos,
                fov,
                cameraLength,
                xRot,
                yRot,
                (int) (x / scaleX),
                (int) ((x + width) / scaleX),
                (int) (y / scaleY),
                (int) ((y + height) / scaleY),
                1.0f,
                depress_sphere.get(),
                lodDistance.get(),
                false,
                0,
                null,
                new ScreenRectangle((int) (x / scaleX), (int) (y / scaleY), (int) (width / scaleX), (int) (height / scaleY))
        );
    }
    
    @Override
    public void doRender(IGUIGraphics graphics, int mouseX, int mouseY, float a) {
        if (graphics instanceof B3dGuiGraphics b3dGuiGraphics && lastState != null) {
            var inner = b3dGuiGraphics.getInner();
            inner.submitPictureInPictureRenderState(lastState);
            if (player.get()) this.renderPlayerHead(b3dGuiGraphics);
            if (compass.get())
                CompassRenderer.render(b3dGuiGraphics, x + 8, y + 10, x + width, y + height, yRot, 6, 24f);
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
    }
    
    public void renderPlayerHead(B3dGuiGraphics guiGraphics) {
        var level = Minecraft.getInstance().level;
        var player = Minecraft.getInstance().player;
        if (level == null || player == null || lastState == null) return;
        var playInfos = player.connection.getListedOnlinePlayers();
        for (var p : playInfos) {
            var uuid = p.getProfile().id();
            var entity = level.getEntity(uuid);
            if (entity == null || !lastState.frustum().isVisible(entity.getBoundingBox())) continue;
            var pos = lastState.projWorld2Screen(this, entity.position().toVector3f().add(0, 2f, 0));
            var px = pos.x - 8;
            var py = pos.y - 10;
            PlayerFaceExtractor.extractRenderState(guiGraphics.getInner(), p.getSkin(), (int) px, (int) py, 16);
            py -= 10;
            guiGraphics.drawCenteredString(p.getProfile().name(), pos.x, py, -1);
        }
    }
    
    public @Nullable Vector3f projScreen2World(double screenX, double screenY) {
        return this.projScreen2World((float) screenX, (float) screenY);
    }
    
    public @Nullable Vector3f projScreen2World(float screenX, float screenY) {
        if (lastState == null) return null;
        var storage = TerrainChunkManager.INSTANCE.getCurrentLevelChunkStorage();
        if (storage == null) return null;
        return lastState.projScreen2World(this, storage, screenX, screenY);
    }
    
    public @Nullable Vector2f projWorld2Screen(Vector3f worldPos) {
        if (this.lastState == null) {
            return null;
        }
        if (!this.lastState.frustum().isVisible(new AABB(worldPos.x, worldPos.y, worldPos.z, worldPos.x + 1, worldPos.y + 1, worldPos.z + 1))) {
            return null;
        }
        return this.lastState.projWorld2Screen(this, worldPos);
    }
    
    @Override
    public boolean mouseMoved(double mouseX, double mouseY) {
        return super.mouseMoved(mouseX, mouseY);
    }
    
    private String vec3fToString(Vector3f vec) {
        return String.format("( %.2f, %.2f, %.2f )", vec.x(), vec.y(), vec.z());
    }
    
    private Vector3f dirVec() {
        var x = (float) (Math.cos(Math.toRadians(xRot)) * Math.sin(Math.toRadians(yRot)));
        var y = (float) (Math.sin(Math.toRadians(xRot)));
        var z = (float) (Math.cos(Math.toRadians(xRot)) * Math.cos(Math.toRadians(yRot)));
        return new Vector3f(x, y, z).normalize();
    }
    
    @Override
    protected boolean onMouseClicked(IMouseButtonEvent event, boolean doubleClick) {
        if (this.dispatchMapEvent(new MapInputEvent.MouseClicked(event, doubleClick))) {
            return true;
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
        if (this.screenSession != null) {
            this.screenSession.tick();
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
        if (this.screenSession == null) {
            return false;
        }
        return this.screenSession.handle(event);
    }
}
