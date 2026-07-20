package com.xkball.x3dmap.client.map.minimap;

import com.xkball.x3dmap.ClientConfig;
import com.xkball.x3dmap.api.client.gui.input.MapInputEvent;
import com.xkball.x3dmap.api.client.gui.input.MapInputResult;
import com.xkball.x3dmap.api.client.render.Map2dLayerPhase;
import com.xkball.x3dmap.api.client.render.MapViewportPresets;
import com.xkball.x3dmap.api.client.viewport.MapCameraState;
import com.xkball.x3dmap.client.map.render.Map2dLayerRenderer;
import com.xkball.x3dmap.client.map.render.Map2dRenderContextImpl;
import com.xkball.x3dmap.client.map.viewport.MapFrameSnapshot;
import com.xkball.x3dmap.client.map.viewport.MapRenderViewport;
import com.xkball.x3dmap.client.render.pip.WorldTerrainPipRenderer;
import com.xkball.x3dmap.client.terrain.TerrainChunkManager;
import com.xkball.xklib.api.gui.input.IMouseButtonEvent;
import com.xkball.xklib.ui.layout.BooleanLayoutVariable;
import com.xkball.xklib.ui.layout.IntLayoutVariable;
import com.xkball.xklib.ui.render.IGUIGraphics;
import com.xkball.xklib.ui.widget.container.ContainerWidget;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.ui.XKLibBaseScreen;
import com.xkball.xklibmc.x3d.backend.b3d.B3dGuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jspecify.annotations.Nullable;

@NonNullByDefault
public class MinimapPreviewWidget extends ContainerWidget {

    private boolean dragging;
    private WorldTerrainPipRenderer.@Nullable WorldTerrainState lastState;
    private final MapRenderViewport viewport;

    private final IntLayoutVariable highDetailRange;
    private final BooleanLayoutVariable rotateWithPlayer;

    public MinimapPreviewWidget(IntLayoutVariable highDetailRange, BooleanLayoutVariable rotateWithPlayer) {
        this.highDetailRange = highDetailRange;
        this.rotateWithPlayer = rotateWithPlayer;
        var level = Minecraft.getInstance().level;
        var dimension = level == null ? net.minecraft.world.level.Level.OVERWORLD : level.dimension();
        this.viewport = new MapRenderViewport(
                TerrainChunkManager.INSTANCE.mapPluginRegistry.runtime(),
                this,
                dimension,
                MapViewportPresets.MINIMAP,
                new MapCameraState(0, 0, 0, 89, 0, 0, 60)
        );
        this.setOverflow(false);
    }

    @Override
    public void doRender(IGUIGraphics graphics, int mouseX, int mouseY, float a) {
        this.viewport.tick();
        if (this.viewport.invalidated()) {
            this.calculateNewPipState();
        }
        if (graphics instanceof B3dGuiGraphics b3dGuiGraphics && lastState != null) {
            var inner = b3dGuiGraphics.getInner();
            inner.submitPictureInPictureRenderState(lastState);
            var context = new Map2dRenderContextImpl(this.lastState.frame(), graphics);
            Map2dLayerRenderer.render(this.lastState.layers(), Map2dLayerPhase.CONTENT, context);
            var x0 = (int) x;
            var y0 = (int) y;
            var x1 = (int) (x + width);
            var y1 = (int) (y + height);
            MinimapRenderHelper.drawBorder(inner, x0, y0, x1, y1);
            var player = Minecraft.getInstance().player;
            if (player != null) {
                var yRot = rotateWithPlayer.get() ? MinimapPlayerMarker.mapYawForPlayerUp(player.getYRot()) : 0.0f;
                CompassRenderer.render(b3dGuiGraphics, x0 + 2, y0 + 2, x1, y1, yRot, 0, 24f);
                MinimapPlayerMarker.render(b3dGuiGraphics, x0, y0, x1, y1, player.getYRot(), rotateWithPlayer.get());
            }
        }
        if (this.lastState != null) {
            var context = new Map2dRenderContextImpl(this.lastState.frame(), graphics);
            Map2dLayerRenderer.render(this.lastState.layers(), Map2dLayerPhase.FOREGROUND, context);
        }
        super.doRender(graphics, mouseX, mouseY, a);
    }

    @Override
    public void resize(float offsetX, float offsetY) {
        super.resize(offsetX, offsetY);
        this.calculateNewPipState();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (this.viewport.handle(new MapInputEvent.MouseScrolled(x, y, scrollX, scrollY)) != MapInputResult.PASS) {
            return true;
        }
        var fov = ClientConfig.MINIMAP_CAMERA_FOV.get().floatValue();
        var cameraLength = ClientConfig.MINIMAP_CAMERA_LENGTH.get().floatValue();
        if (fov > 90 - 1e-6) {
            cameraLength = Math.max(cameraLength - (float) (scrollY * Math.log10(cameraLength + 10f)), 0);
            ClientConfig.MINIMAP_CAMERA_LENGTH.set((double) cameraLength);
        }
        if (ClientConfig.MINIMAP_CAMERA_LENGTH.get() < 1e-6) {
            ClientConfig.MINIMAP_CAMERA_FOV.set(Math.clamp(fov - scrollY, 5, 90));
        }
        this.calculateNewPipState();
        return true;
    }

    @Override
    protected boolean onMouseClicked(IMouseButtonEvent event, boolean doubleClick) {
        if (this.viewport.handle(new MapInputEvent.MouseClicked(event, doubleClick)) != MapInputResult.PASS) {
            return true;
        }
        dragging = true;
        return true;
    }

    @Override
    protected boolean onMouseReleased(IMouseButtonEvent event) {
        if (this.viewport.handle(new MapInputEvent.MouseReleased(event)) != MapInputResult.PASS) {
            return true;
        }
        dragging = false;
        return true;
    }

    @Override
    protected boolean onMouseDragged(IMouseButtonEvent event, double dx, double dy) {
        if (this.viewport.handle(new MapInputEvent.MouseDragged(event, dx, dy)) != MapInputResult.PASS) {
            return true;
        }
        if (!dragging) {
            return false;
        }
        float sens = 0.25f * Math.max(0.4f, ClientConfig.MINIMAP_CAMERA_FOV.get().floatValue() / 100);
        var xRot = ClientConfig.MINIMAP_CAMERA_X_ROT.get().floatValue();
        ClientConfig.MINIMAP_CAMERA_X_ROT.set((double) Math.clamp(xRot + (float) dy * sens, -89.9f, 89.9f));
        this.calculateNewPipState();
        return true;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public boolean mouseMoved(double mouseX, double mouseY) {
        if (this.viewport.handle(new MapInputEvent.MouseMoved(mouseX, mouseY)) != MapInputResult.PASS) {
            return true;
        }
        return super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public void onRemove() {
        this.viewport.close();
        super.onRemove();
    }

    private void calculateNewPipState() {
        if (width == 0 || height == 0) {
            lastState = null;
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            lastState = null;
            return;
        }
        var player = mc.player;
        var blockPos = player.blockPosition();
        var targetY = MinimapRenderHelper.getCameraTargetY(player.getY(), blockPos.getX(), blockPos.getZ(), mc.level.getMinY());
        var yRot = rotateWithPlayer.get() ? MinimapPlayerMarker.mapYawForPlayerUp(player.getYRot()) : 0.0f;
        var camera = new MapCameraState(
                (float) player.getX(),
                targetY,
                (float) player.getZ(),
                ClientConfig.MINIMAP_CAMERA_X_ROT.get().floatValue(),
                yRot,
                ClientConfig.MINIMAP_CAMERA_LENGTH.get().floatValue(),
                ClientConfig.MINIMAP_CAMERA_FOV.get().floatValue()
        );
        var scaleX = XKLibBaseScreen.tryGetScaleX();
        var scaleY = XKLibBaseScreen.tryGetScaleY();
        var frame = new MapFrameSnapshot(
                mc.level.dimension(),
                MapViewportPresets.MINIMAP,
                camera,
                this.x,
                this.y,
                this.width,
                this.height,
                false,
                512,
                this.highDetailRange.get(),
                mc.level.getMinY(),
                TerrainChunkManager.INSTANCE.getCurrentLevelChunkStorage()
        );
        lastState = new WorldTerrainPipRenderer.WorldTerrainState(
                frame,
                this.viewport.prepare(frame),
                (int) (x / scaleX),
                (int) ((x + width) / scaleX),
                (int) (y / scaleY),
                (int) ((y + height) / scaleY),
                1.0f,
                null,
                new ScreenRectangle((int) (x / scaleX), (int) (y / scaleY), (int) (width / scaleX), (int) (height / scaleY))
        );
    }
}
