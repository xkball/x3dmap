package com.xkball.x3dmap.compat.smu;

import com.xkball.x3dmap.api.client.map.IWaypointOverlayWidget;
import com.xkball.xklib.api.gui.input.IMouseButtonEvent;
import com.xkball.xklib.resource.ResourceLocation;
import com.xkball.xklib.ui.render.IGUIGraphics;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.x3d.backend.b3d.B3dGuiGraphics;
import org.joml.Vector2d;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.teacon.exhibition_portal.components.ExhibitionMetadata;

import java.util.function.Consumer;

@NonNullByDefault
public final class SmuExhibitionWidget extends Widget implements IWaypointOverlayWidget {

    private static final ResourceLocation PINNED_ICON = new ResourceLocation("x3d_map", "icon/museum");
    private final ExhibitionMetadata metadata;
    private final Consumer<Vector2d> openAction;

    public SmuExhibitionWidget(ExhibitionMetadata metadata, Consumer<Vector2d> openAction) {
        this.metadata = metadata;
        this.openAction = openAction;
        this.inlineStyle("size: 8rpx 8rpx;");
    }

    @Override
    public Vector3fc worldPosition() {
        var waypoint = this.metadata.waypoint();
        return new Vector3f(waypoint.x() + 0.5f, waypoint.y(), waypoint.z() + 0.5f);
    }

    @Override
    public void doRender(IGUIGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.doRender(graphics, mouseX, mouseY, partialTick);
        if (graphics instanceof B3dGuiGraphics guiGraphics) {
            var scale = guiGraphics.scale / 2;
            var x = this.getX();
            var y = this.getY();
            var textWidth = graphics.defaultFont().width(this.metadata.name(), 14);
            graphics.fillRounded(x, y, x + (16 + textWidth + 2) * scale, y + 16 * scale, 0x88000000, 4);
            graphics.blitSprite(PINNED_ICON, x + scale, y + scale, 14 * scale, 14 * scale, -1);
            graphics.drawString(this.metadata.name(), x + 16 * scale, y + 2 * scale, -1, 14 * scale);
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
