package com.xkball.x3dmap.ui.widget;

import com.xkball.x3dmap.client.render.pip.TerrainProjectorPipRenderer;
import com.xkball.xklib.ui.render.IGUIGraphics;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.ui.XKLibBaseScreen;
import com.xkball.xklibmc.x3d.backend.b3d.B3dGuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;

@NonNullByDefault
public final class TerrainProjectorPipWidget extends Widget {

    @Override
    public void doRender(IGUIGraphics graphics, int mouseX, int mouseY, float a) {
        if (graphics instanceof B3dGuiGraphics b3dGraphics) {
            var scaleX = XKLibBaseScreen.tryGetScaleX();
            var scaleY = XKLibBaseScreen.tryGetScaleY();
            var x0 = (int) (this.x / scaleX);
            var y0 = (int) (this.y / scaleY);
            var x1 = (int) ((this.x + this.width) / scaleX);
            var y1 = (int) ((this.y + this.height) / scaleY);
            b3dGraphics.getInner().submitPictureInPictureRenderState(new TerrainProjectorPipRenderer.TerrainProjectorState(
                    x0,
                    x1,
                    y0,
                    y1,
                    1.0F,
                    null,
                    new ScreenRectangle(x0, y0, x1 - x0, y1 - y0)
            ));
        }
        super.doRender(graphics, mouseX, mouseY, a);
    }
}
