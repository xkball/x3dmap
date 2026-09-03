package com.xkball.x3dmap.client.map.minimap;

import com.xkball.xklibmc.x3d.backend.b3d.B3dGuiGraphics;
import net.minecraft.client.Minecraft;

public final class CompassRenderer {
    
    private static final int N_COLOR = 0xFFFF5555;
    private static final int LABEL_COLOR = 0xFFFFFFFF;
    private static final int BG_COLOR = 0x90505050;
    private static final float BACKGROUND_PADDING = 1f;
    
    private CompassRenderer() {
    }
    
    public static void render(B3dGuiGraphics graphics, float x0, float y0, float x1, float y1, float yRot, float margin, float fontHeight) {
        float cx = (x0 + x1) / 2f;
        float cy = (y0 + y1) / 2f;
        float halfW = (x1 - x0) / 2f - margin;
        float halfH = (y1 - y0) / 2f - margin;
        var labelInset = fontHeight / 2f + BACKGROUND_PADDING;
        halfW -= labelInset;
        halfH -= labelInset;
        if (halfW <= 10 || halfH <= 10) return;
        
        var yRotClamped = ((yRot % 360) + 360) % 360;
        
        placeLabel(graphics, cx, cy, halfW, halfH, (float) Math.toRadians(yRotClamped), "N", N_COLOR, fontHeight);
        placeLabel(graphics, cx, cy, halfW, halfH, (float) Math.toRadians(yRotClamped + 90), "E", LABEL_COLOR, fontHeight);
        placeLabel(graphics, cx, cy, halfW, halfH, (float) Math.toRadians(yRotClamped + 180), "S", LABEL_COLOR, fontHeight);
        placeLabel(graphics, cx, cy, halfW, halfH, (float) Math.toRadians(yRotClamped + 270), "W", LABEL_COLOR, fontHeight);
    }
    
    private static void placeLabel(B3dGuiGraphics graphics, float cx, float cy, float halfW, float halfH, float theta, String label, int color, float fontHeight) {
        float dx = (float) Math.sin(theta);
        float dy = -(float) Math.cos(theta);
        
        float tx = Float.POSITIVE_INFINITY;
        float ty = Float.POSITIVE_INFINITY;
        if (Math.abs(dx) > 1e-10f) tx = halfW / Math.abs(dx);
        if (Math.abs(dy) > 1e-10f) ty = halfH / Math.abs(dy);
        float t = Math.min(tx, ty);
        
        float x = cx + dx * t;
        float y = cy + dy * t;
        float textY = y - fontHeight / 2f;
        
        var font = Minecraft.getInstance().font;
        var scale = fontHeight / (float) font.lineHeight;
        var textWidth = font.width(label) * scale;
        var bgX0 = x - textWidth / 2f - BACKGROUND_PADDING;
        var bgY0 = textY - BACKGROUND_PADDING;
        var bgX1 = x + textWidth / 2f + BACKGROUND_PADDING;
        var bgY1 = textY + fontHeight + BACKGROUND_PADDING;
        graphics.fill(bgX0, bgY0, bgX1, bgY1, BG_COLOR);
        graphics.drawCenteredString(label, x, textY, color, true, fontHeight);
    }
}
