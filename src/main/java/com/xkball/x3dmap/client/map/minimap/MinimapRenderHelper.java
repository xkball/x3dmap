package com.xkball.x3dmap.client.map.minimap;

import com.xkball.x3dmap.client.terrain.TerrainChunkManager;
import com.xkball.xklib.XKLib;
import com.xkball.xklibmc.x3d.backend.b3d.B3dGuiGraphics;
import com.xkball.xklibmc.x3d.backend.b3d.B3dRenderContext;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.client.gui.GuiGraphicsExtractor;
@NonNullByDefault
public final class MinimapRenderHelper {

    private MinimapRenderHelper() {
    }

    public static void drawBorder(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y0, 0xCCAAAAAA);
        graphics.fill(x0 - 1, y1, x1 + 1, y1 + 1, 0xCCAAAAAA);
        graphics.fill(x0 - 1, y0, x0, y1, 0xCCAAAAAA);
        graphics.fill(x1, y0, x1 + 1, y1, 0xCCAAAAAA);
    }

    public static float getCameraTargetY(double playerY, int blockX, int blockZ, int minY) {
        var storage = TerrainChunkManager.INSTANCE.getCurrentLevelChunkStorage();
        if (storage != null) {
            var h = storage.getHeight(blockX, blockZ);
            if (h != minY && playerY < h) {
                return h;
            }
        }
        return (float) playerY;
    }

    public static B3dGuiGraphics getOrCreateB3dGuiGraphics(GuiGraphicsExtractor graphics) {
        if (XKLib.RENDER_CONTEXT.get() == null) {
            XKLib.RENDER_CONTEXT.set(new B3dRenderContext());
        }
        var guiGraphics = (B3dGuiGraphics) XKLib.RENDER_CONTEXT.get().getGUIGraphics();
        guiGraphics.setInner(graphics);
        return guiGraphics;
    }
}
