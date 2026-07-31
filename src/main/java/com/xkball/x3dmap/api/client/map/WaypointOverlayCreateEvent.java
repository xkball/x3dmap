package com.xkball.x3dmap.api.client.map;

import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.neoforged.bus.api.Event;

import java.util.ArrayList;
import java.util.List;

@NonNullByDefault
public final class WaypointOverlayCreateEvent extends Event {

    private final List<Widget> widgets = new ArrayList<>();

    public <T extends Widget & IWaypointOverlayWidget> void add(T widget) {
        this.widgets.add(widget);
    }

    public List<Widget> widgets() {
        return List.copyOf(this.widgets);
    }
}
