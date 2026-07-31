package com.xkball.x3dmap.client.map.waypoint;

import com.xkball.x3dmap.api.client.map.IWaypointOverlayWidget;
import com.xkball.x3dmap.api.client.map.WaypointOverlayCreateEvent;
import com.xkball.x3dmap.api.client.viewport.IMapProjection;
import com.xkball.xklib.ui.layout.BooleanLayoutVariable;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklib.ui.widget.container.AbsoluteContainer;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import org.apache.commons.lang3.function.TriConsumer;
import org.joml.Vector2d;
import org.jspecify.annotations.Nullable;
import net.neoforged.neoforge.common.NeoForge;

import java.util.function.Supplier;

@NonNullByDefault
public class WaypointOverlayWidget extends AbsoluteContainer {

    private final IMapProjection projection;
    private final TriConsumer<Vector2d, Waypoint, Boolean> openHandler;

    public WaypointOverlayWidget(IMapProjection projection, BooleanLayoutVariable visible, Supplier<WaypointStorage> storage, Supplier<@Nullable Waypoint> temporary, TriConsumer<Vector2d, Waypoint, Boolean> openHandler) {
        this.projection = projection;
        this.openHandler = openHandler;
        this.autoReorder = false;
        if (visible.get()) {
            for (var waypoint : storage.get().waypoints()) {
                if (!waypoint.hidden()) {
                    this.addWaypointIcon(waypoint, false);
                }
            }
            var temporaryWaypoint = temporary.get();
            if (temporaryWaypoint != null) {
                this.addWaypointIcon(temporaryWaypoint, true);
            }
        }
        var event = new WaypointOverlayCreateEvent();
        NeoForge.EVENT_BUS.post(event);
        for (var widget : event.widgets()) {
            this.addChild(widget);
        }
    }

    @Override
    public void resize(float offsetX, float offsetY) {
        this.updatePositions();
        super.resize(offsetX, offsetY);
    }

    public void updatePositions() {
        for (var child : this.children) {
            if (child instanceof IWaypointOverlayWidget waypointWidget) {
                this.updateWidgetPosition(child, waypointWidget);
            }
        }
    }

    private void addWaypointIcon(Waypoint waypoint, boolean temporary) {
        var icon = new WaypointIconWidget(waypoint, temporary, (p) -> this.openHandler.accept(p, waypoint, temporary));
        this.addChild(icon);
    }

    private void updateWidgetPosition(Widget widget, IWaypointOverlayWidget waypointWidget) {
        var screen = this.projection.worldToScreen(waypointWidget.worldPosition());
        if (screen != null) {
            widget.setAbsoluteSize(screen.x - this.getX(), screen.y - this.getY() - 16);
            widget.setStyle(s -> s.display = TaffyDisplay.DEFAULT);
        } else {
            widget.setStyle(s -> s.display = TaffyDisplay.NONE);
        }
    }
}
