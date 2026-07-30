package com.xkball.x3dmap.api.client.gui;

import com.xkball.x3dmap.api.client.runtime.IX3dMapRuntime;
import com.xkball.x3dmap.api.client.viewport.IMapViewport;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.widget.IconCheckBox;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.utils.VanillaUtils;
import net.minecraft.resources.Identifier;

@NonNullByDefault
public interface IMapScreenContext {

    Identifier extensionId();

    IX3dMapRuntime runtime();

    IMapViewport viewport();

    IMapGui gui();

    default void addLayerToggle(Identifier layerId, Identifier sprite, String tooltipKey) {
        var layers = this.viewport().layers();
        var button = new IconCheckBox(VanillaUtils.convertId(sprite));
        button.setValue(layers.visible(layerId));
        button.onChange = () -> layers.setVisible(layerId, button.getValue());
        button.withTooltip(IComponent.translatable(tooltipKey));
        this.gui().addToolbarWidget(MapToolbarSlot.LEFT, button);
    }
}
