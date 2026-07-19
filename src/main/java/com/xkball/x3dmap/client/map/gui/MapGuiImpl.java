package com.xkball.x3dmap.client.map.gui;

import com.xkball.x3dmap.api.client.gui.IMapGui;
import com.xkball.x3dmap.api.client.gui.IMapWindow;
import com.xkball.x3dmap.api.client.gui.MapToolbarSlot;
import com.xkball.x3dmap.api.client.gui.MapWindowSpec;
import com.xkball.x3dmap.ui.widget.WorldTerrainWidget;
import com.xkball.xklib.ui.system.GuiSystem;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklib.ui.widget.container.WindowedContainer;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

@NonNullByDefault
public final class MapGuiImpl implements IMapGui {

    private final WorldTerrainWidget widget;

    public MapGuiImpl(WorldTerrainWidget widget) {
        this.widget = widget;
    }

    @Override
    public void addToolbarWidget(MapToolbarSlot slot, Widget widget) {
        switch (slot) {
            case LEFT -> this.widget.addExtensionLeftBarWidget(widget);
            case TOP_PRIMARY -> this.widget.addExtensionTopBar1Widget(widget);
            case TOP_SECONDARY -> this.widget.addExtensionTopBar2Widget(widget);
        }
    }

    @Override
    public IMapWindow openWindow(MapWindowSpec spec, Widget content) {
        var parent = this.widget.windowLayer();
        var width = spec.width().resolve(parent.getWidth());
        var height = spec.height().resolve(parent.getHeight());
        var x = Float.isNaN(spec.x()) ? Math.max(0f, (parent.getWidth() - width) / 2f) : spec.x();
        var y = Float.isNaN(spec.y()) ? Math.max(0f, (parent.getHeight() - height) / 2f) : spec.y();
        WindowedContainer.SubWindow window;
        if (spec.blocking()) {
            var layer = new WindowedContainer();
            layer.setAutoRemoveFromGuiSystemWhenEmpty(true);
            layer.inlineStyle("size: 100% 100%; background-color: 0x55000000;");
            layer.setBlockInput(true);
            window = layer.addSubWindow(content, spec.title(), spec.resizable(), x, y, width, height);
            GuiSystem.INSTANCE.get().insertLayerAfter(layer, parent);
        } else {
            window = parent.addSubWindow(content, spec.title(), spec.resizable(), x, y, width, height);
        }
        window.setAutoHeight(spec.autoShrinkHeight());
        return new MapWindowImpl(window);
    }

    @Override
    public void setOverlay(Identifier id, Supplier<Widget> provider) {
        this.widget.inner.setExtensionOverlayProvider(id.toString(), provider);
    }

    @Override
    public void refreshOverlays() {
        this.widget.inner.refreshExtensionOverlays();
    }
}
