package com.xkball.x3dmap.client.map.waypoint;

import com.xkball.x3dmap.api.client.map.WorldMapExtensionService;
import com.xkball.xklib.ui.layout.BooleanLayoutVariable;
import com.xkball.xklib.ui.widget.container.AbsoluteContainer;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import org.apache.commons.lang3.function.TriConsumer;
import org.joml.Vector2d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public class WaypointOverlayWidget extends AbsoluteContainer {
    
    private final WorldMapExtensionService service;
    private final TriConsumer<Vector2d, Waypoint, Boolean> openHandler;
    
    public WaypointOverlayWidget(WorldMapExtensionService service, BooleanLayoutVariable visible, Supplier<WaypointStorage> storage, Supplier<@Nullable Waypoint> temporary, TriConsumer<Vector2d, Waypoint, Boolean> openHandler) {
        this.service = service;
        this.openHandler = openHandler;
        this.autoReorder = false;
        if (!visible.get()) {
            return;
        }
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
    
    @Override
    public void resize(float offsetX, float offsetY) {
        this.updatePositions();
        super.resize(offsetX, offsetY);
    }
    
    public void updatePositions() {
        for (var child : this.children) {
            if (child instanceof WaypointIconWidget icon) {
                this.updateIconPosition(icon);
            }
        }
    }
    
    private void addWaypointIcon(Waypoint waypoint, boolean temporary) {
        var icon = new WaypointIconWidget(waypoint, temporary, (p) -> this.openHandler.accept(p, waypoint, temporary));
        this.addChild(icon);
    }
    
    private void updateIconPosition(WaypointIconWidget icon) {
        var pos = icon.waypoint().pos();
        var screen = this.service.projWorld2Screen(new Vector3f(pos.getX(), pos.getY(), pos.getZ()));
        if (screen != null) {
            icon.setAbsoluteSize(screen.x - this.getX(), screen.y - this.getY() - 16);
            icon.setStyle(s -> s.display = TaffyDisplay.DEFAULT);
        } else {
            icon.setStyle(s -> s.display = TaffyDisplay.NONE);
        }
    }
}
