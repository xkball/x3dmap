package com.xkball.x3dmap.client.map.waypoint;

import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklib.api.gui.input.IMouseButtonEvent;
import com.xkball.xklib.resource.ResourceLocation;
import com.xkball.xklib.ui.render.IGUIGraphics;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklibmc.x3d.backend.b3d.B3dGuiGraphics;
import org.joml.Vector2d;

import java.util.function.Consumer;

public class WaypointIconWidget extends Widget {
    
    
    private static final ResourceLocation PINNED_ICON = VanillaUtils.modrl("icon/pinned");
    
    private final Waypoint waypoint;
    private final boolean temporary;
    private final Consumer<Vector2d> openAction;
    
    public WaypointIconWidget(Waypoint waypoint, boolean temporary, Consumer<Vector2d> openAction) {
        this.waypoint = waypoint;
        this.temporary = temporary;
        this.openAction = openAction;
        this.inlineStyle("size: 8rpx 8rpx;");
    }
    
    public Waypoint waypoint() {
        return this.waypoint;
    }
    
    @Override
    public void doRender(IGUIGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.doRender(graphics, mouseX, mouseY, partialTick);
        if (graphics instanceof B3dGuiGraphics guiGraphics) {
            var scale = guiGraphics.scale / 2;
            var color = this.temporary ? 0xFFFFFFFF : this.waypoint.color();
            var x0 = this.getX();
            var y0 = this.getY();
            var textWidth = graphics.defaultFont().width(this.waypoint.name(), 14);
            graphics.fillRounded(x0, y0, x0 + (16 + textWidth + 2) * scale, y0 + 16 * scale, 0x88000000, 4);
            graphics.blitSprite(PINNED_ICON, x0 + scale, y0 + scale, 14 * scale, 14 * scale, color);
            graphics.drawString(this.waypoint.name(), x0 + 16 * scale, y0 + 2 * scale, -1, 14 * scale);
        }
        
    }
    
    @Override
    protected boolean onMouseClicked(IMouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            this.openAction.accept(new Vector2d(event.x(), event.y()));
            return true;
        }
        return false;
    }
}
