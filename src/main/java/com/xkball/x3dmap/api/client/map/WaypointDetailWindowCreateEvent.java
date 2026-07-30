package com.xkball.x3dmap.api.client.map;

import com.xkball.x3dmap.client.map.waypoint.Waypoint;
import com.xkball.xklib.ui.widget.Button;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.neoforged.bus.api.Event;

import java.util.List;

@NonNullByDefault
public final class WaypointDetailWindowCreateEvent extends Event {

    private final Waypoint waypoint;
    private final boolean temporary;
    private final List<Button> buttons;

    public WaypointDetailWindowCreateEvent(Waypoint waypoint, boolean temporary, List<Button> buttons) {
        this.waypoint = waypoint;
        this.temporary = temporary;
        this.buttons = buttons;
    }

    public Waypoint waypoint() {
        return this.waypoint;
    }

    public boolean temporary() {
        return this.temporary;
    }

    public List<Button> buttons() {
        return this.buttons;
    }
}
