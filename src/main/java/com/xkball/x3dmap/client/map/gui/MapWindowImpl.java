package com.xkball.x3dmap.client.map.gui;

import com.xkball.x3dmap.api.client.gui.IMapWindow;
import com.xkball.xklib.ui.widget.container.WindowedContainer;
import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
final class MapWindowImpl implements IMapWindow {

    private final WindowedContainer.SubWindow window;

    MapWindowImpl(WindowedContainer.SubWindow window) {
        this.window = window;
    }

    @Override
    public boolean visible() {
        return this.window.visible();
    }

    @Override
    public void close() {
        this.window.close();
    }
}
