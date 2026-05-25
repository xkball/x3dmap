package com.xkball.x3dmap.client.map.selection;

import com.xkball.xklib.ui.render.IGUIGraphics;
import com.xkball.xklib.ui.widget.Widget;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

public class SelectionRectangleWidget extends Widget {

    private @Nullable Vector2f start;
    private @Nullable Vector2f end;

    public SelectionRectangleWidget() {
        this.inlineStyle("size: 100% 100%;");
        this.setEnabled(false);
    }

    public void setStart(@Nullable Vector2f start) {
        this.start = start;
    }

    public void setEnd(@Nullable Vector2f end) {
        this.end = end;
    }

    public void clear() {
        this.start = null;
        this.end = null;
    }

    @Override
    public void doRender(IGUIGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.doRender(graphics, mouseX, mouseY, partialTick);
        if (this.start == null || this.end == null) {
            return;
        }
        var x0 = Math.min(this.start.x, this.end.x);
        var y0 = Math.min(this.start.y, this.end.y);
        var x1 = Math.max(this.start.x, this.end.x);
        var y1 = Math.max(this.start.y, this.end.y);
        graphics.fill(x0, y0, x1, y1, 0x44FFFF00);
        var border = 1f;
        graphics.fill(x0, y0, x1, y0 + border, 0xFFFFFF00);
        graphics.fill(x0, y1 - border, x1, y1, 0xFFFFFF00);
        graphics.fill(x0, y0, x0 + border, y1, 0xFFFFFF00);
        graphics.fill(x1 - border, y0, x1, y1, 0xFFFFFF00);
    }
}
