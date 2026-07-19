package com.xkball.x3dmap.client.map.minimap;

import com.xkball.x3dmap.ClientConfig;
import com.xkball.x3dmap.client.render.pip.WorldTerrainPipRenderer;
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
import org.joml.Vector3f;

@NonNullByDefault
public class MinimapPreviewWidget extends ContainerWidget {
    
    private boolean dragging;
    private WorldTerrainPipRenderer.WorldTerrainState lastState;
    
    private final IntLayoutVariable highDetailRange;
    private final BooleanLayoutVariable rotateWithPlayer;
    
    public MinimapPreviewWidget(IntLayoutVariable highDetailRange, BooleanLayoutVariable rotateWithPlayer) {
        this.highDetailRange = highDetailRange;
        this.rotateWithPlayer = rotateWithPlayer;
        this.setOverflow(false);
    }
    
    @Override
    public void doRender(IGUIGraphics graphics, int mouseX, int mouseY, float a) {
        if (graphics instanceof B3dGuiGraphics b3dGuiGraphics && lastState != null) {
            var inner = b3dGuiGraphics.getInner();
            inner.submitPictureInPictureRenderState(lastState);
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
        super.doRender(graphics, mouseX, mouseY, a);
    }
    
    @Override
    public void resize(float offsetX, float offsetY) {
        super.resize(offsetX, offsetY);
        this.calculateNewPipState();
    }
    
    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        var fov = ClientConfig.MINIMAP_CAMERA_FOV.get().floatValue();
        var cameraLength = ClientConfig.MINIMAP_CAMERA_LENGTH.get().floatValue();
        if (fov > 90 - 1e-6) {
            cameraLength = Math.max(cameraLength - (float) (scrollY * Math.log10(cameraLength + 10f)), 0);
            ClientConfig.MINIMAP_CAMERA_LENGTH.set((double) cameraLength);
        }
        if (ClientConfig.MINIMAP_CAMERA_LENGTH.get() < 1e-6) {
            ClientConfig.MINIMAP_CAMERA_FOV.set((double) Math.clamp(fov - scrollY, 5, 90));
        }
        this.calculateNewPipState();
        return true;
    }
    
    @Override
    protected boolean onMouseClicked(IMouseButtonEvent event, boolean doubleClick) {
        dragging = true;
        return true;
    }
    
    @Override
    protected boolean onMouseReleased(IMouseButtonEvent event) {
        dragging = false;
        return true;
    }
    
    @Override
    protected boolean onMouseDragged(IMouseButtonEvent event, double dx, double dy) {
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
        var target = new Vector3f((float) player.getX(), targetY, (float) player.getZ());
        var layers = MinimapRenderHelper.buildEnabledLayers();
        var yRot = rotateWithPlayer.get() ? MinimapPlayerMarker.mapYawForPlayerUp(player.getYRot()) : 0.0f;
        var scaleX = XKLibBaseScreen.tryGetScaleX();
        var scaleY = XKLibBaseScreen.tryGetScaleY();
        lastState = new WorldTerrainPipRenderer.WorldTerrainState(
                layers,
                target,
                blockPos,
                ClientConfig.MINIMAP_CAMERA_FOV.get().floatValue(),
                ClientConfig.MINIMAP_CAMERA_LENGTH.get().floatValue(),
                ClientConfig.MINIMAP_CAMERA_X_ROT.get().floatValue(),
                yRot,
                (int) (x / scaleX),
                (int) ((x + width) / scaleX),
                (int) (y / scaleY),
                (int) ((y + height) / scaleY),
                1.0f,
                false,
                512,
                true,
                highDetailRange.get(),
                null,
                new ScreenRectangle((int) (x / scaleX), (int) (y / scaleY), (int) (width / scaleX), (int) (height / scaleY))
        );
    }
}
