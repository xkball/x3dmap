package com.xkball.x3dmap.api.client.gui;

import com.xkball.xklib.ui.widget.container.ContainerWidget;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.jspecify.annotations.Nullable;

@NonNullByDefault
public class MapWindowRefContainer extends ContainerWidget {

    private @Nullable IMapWindow window;

    public final void bindWindow(IMapWindow window) {
        this.window = window;
    }

    public final void closeWindow() {
        var currentWindow = this.window;
        if (currentWindow != null) {
            currentWindow.close();
        }
    }
}
