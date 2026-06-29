package com.xkball.x3dmap.client.map.minimap;

import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklib.resource.ResourceLocation;
import com.xkball.xklibmc.x3d.backend.b3d.B3dGuiGraphics;

public final class MinimapPlayerMarker {
    
    private static final ResourceLocation ICON = VanillaUtils.modrl("icon/arrow");
    
    private MinimapPlayerMarker() {
    }
    
    public static float mapYawForPlayerUp(float playerYRot) {
        return normalizeDegrees(180.0f - playerYRot);
    }
    
    public static float iconRotation(float playerYRot, boolean rotateWithPlayer) {
        return normalizeDegrees((rotateWithPlayer ? 180.0f : playerYRot));
    }
    
    public static void render(B3dGuiGraphics graphics, float x0, float y0, float x1, float y1, float playerYRot, boolean rotateWithPlayer) {
        var size = Math.clamp(Math.min(x1 - x0, y1 - y0) * 0.16f, 14.0f, 24.0f);
        var cx = (x0 + x1) * 0.5f;
        var cy = (y0 + y1) * 0.5f;
        render(graphics, cx, cy, size, iconRotation(playerYRot, rotateWithPlayer));
    }
    
    private static void render(B3dGuiGraphics graphics, float cx, float cy, float size, float rotationDegrees) {
        var pose = graphics.getPose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate((float) Math.toRadians(rotationDegrees + 180.0f));
        pose.scale(0.5f, 0.5f);
        pose.translate(-cx, -cy);
        graphics.blitSprite(ICON, cx - size * 0.5f, cy - size * 0.5f, size, size, -1);
        pose.popMatrix();
    }
    
    private static float normalizeDegrees(float degrees) {
        degrees %= 360.0f;
        if (degrees < 0.0f) {
            degrees += 360.0f;
        }
        return degrees;
    }
}
