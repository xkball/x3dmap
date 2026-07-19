package com.xkball.x3dmap.api.client.gui.input;

import com.xkball.xklib.api.gui.input.IKeyEvent;
import com.xkball.xklib.api.gui.input.IMouseButtonEvent;
import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public sealed interface MapInputEvent permits MapInputEvent.MouseClicked, MapInputEvent.MouseReleased, MapInputEvent.MouseDragged, MapInputEvent.MouseScrolled, MapInputEvent.KeyPressed {

    record MouseClicked(IMouseButtonEvent event, boolean doubleClick) implements MapInputEvent {
    }

    record MouseReleased(IMouseButtonEvent event) implements MapInputEvent {
    }

    record MouseDragged(IMouseButtonEvent event, double dx, double dy) implements MapInputEvent {
    }

    record MouseScrolled(double x, double y, double scrollX, double scrollY) implements MapInputEvent {
    }

    record KeyPressed(IKeyEvent event) implements MapInputEvent {
    }
}
