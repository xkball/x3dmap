package com.xkball.x3dmap.api.client.gui;

import com.xkball.x3dmap.api.client.runtime.IX3dMapRuntime;
import com.xkball.x3dmap.api.client.viewport.IMapViewport;
import com.xkball.xklib.resource.ResourceLocation;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.widget.IconCheckBox;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

@NonNullByDefault
public interface IMapScreenContext {

    Identifier extensionId();

    IX3dMapRuntime runtime();

    IMapViewport viewport();

    IMapGui gui();

    default IconCheckBox addLayerToggle(Identifier layerId, ResourceLocation sprite, IComponent tooltip) {
        var layers = this.viewport().layers();
        var button = new IconCheckBox(sprite);
        button.setValue(layers.visible(layerId));
        button.onChange = () -> layers.setVisible(layerId, button.getValue());
        button.withTooltip(tooltip);
        this.gui().addToolbarWidget(MapToolbarSlot.LEFT, button);
        return button;
    }
}
