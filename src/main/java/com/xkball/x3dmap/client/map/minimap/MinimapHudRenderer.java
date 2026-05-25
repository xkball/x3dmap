package com.xkball.x3dmap.client.map.minimap;

import com.xkball.x3dmap.ClientConfig;
import com.xkball.x3dmap.client.map.WorldMapExtensionServiceImpl;
import com.xkball.x3dmap.client.render.pip.WorldTerrainPipRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Vector3f;

public final class MinimapHudRenderer {

    private static final WorldMapExtensionServiceImpl HUD_SERVICE = new WorldMapExtensionServiceImpl("");

    private MinimapHudRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        var mc = Minecraft.getInstance();
        if(mc.level == null || mc.player == null || mc.options.hideGui || mc.screen != null) return;
        if(!ClientConfig.MINIMAP_ENABLED.get()) return;
        var minimap = MinimapExtension.INSTANCE;
        if(minimap == null) return;
        var window = mc.getWindow();
        var guiScaledHeight = window.getGuiScaledHeight();
        var guiScaledWidth = window.getGuiScaledWidth();
        float sizePercent = ClientConfig.MINIMAP_SIZE.get() / 100.0f;
        float paddingPercent = ClientConfig.MINIMAP_PADDING.get() / 100.0f;
        int size = (int) (guiScaledHeight * sizePercent);
        int padding = (int) (guiScaledHeight * paddingPercent);
        int x0 = guiScaledWidth - size - padding;
        int y0 = padding;
        int x1 = x0 + size;
        int y1 = y0 + size;
        var player = mc.player;
        var blockPos = player.blockPosition();
        var targetY = MinimapRenderHelper.getCameraTargetY(player.getY(), blockPos.getX(), blockPos.getZ(), mc.level.getMinY());
        var target = new Vector3f((float) player.getX(), targetY, (float) player.getZ());
        var layers = MinimapRenderHelper.buildEnabledLayers(HUD_SERVICE);
        var yRot = minimap.rotateWithPlayer() ? MinimapPlayerMarker.mapYawForPlayerUp(player.getYRot()) : 0.0f;
        var state = new WorldTerrainPipRenderer.WorldTerrainState(
                layers,
                target,
                blockPos,
                minimap.camFov(),
                minimap.camCameraLength(),
                minimap.camXRot(),
                yRot,
                x0,
                x1,
                y0,
                y1,
                1.0f,
                HUD_SERVICE.getBooleanState("depress_sphere", false),
                HUD_SERVICE.getIntState("lod_distance", 512),
                true,
                minimap.highDetailRange(),
                null,
                new ScreenRectangle(x0, y0, size, size)
        );
        graphics.submitPictureInPictureRenderState(state);
        MinimapRenderHelper.drawBorder(graphics, x0, y0, x1, y1);
        var b3dGraphics = MinimapRenderHelper.getOrCreateB3dGuiGraphics(graphics);
        if(HUD_SERVICE.getBooleanState("compass", true)) CompassRenderer.render(b3dGraphics, x0, y0, x1, y1, yRot, 0, 8f);
        MinimapPlayerMarker.render(b3dGraphics, x0, y0, x1, y1, player.getYRot(), minimap.rotateWithPlayer());
        var coords = "%d %d %d".formatted(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        var textX = (x0 + x1) / 2f;
        var textY = y1 + 8;
        var font = Minecraft.getInstance().font;
        var cw = font.width(coords);
        var ch = font.lineHeight;
        graphics.fill((int) (textX - cw / 2f - 1), (int) (textY - 1), (int) (textX + cw / 2f + 1), (int) (textY + ch + 1), 0x90505050);
        b3dGraphics.drawCenteredString(coords, textX, textY, 0xCCFFFFFF);
    }
}
