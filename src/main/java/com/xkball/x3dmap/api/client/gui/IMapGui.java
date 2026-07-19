package com.xkball.x3dmap.api.client.gui;

import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

@NonNullByDefault
public interface IMapGui {

    void addToolbarWidget(MapToolbarSlot slot, Widget widget);

    IMapWindow openWindow(MapWindowSpec spec, Widget content);

    void setOverlay(Identifier id, Supplier<Widget> provider);

    void refreshOverlays();
}
