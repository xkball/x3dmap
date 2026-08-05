package com.xkball.x3dmap.api.client.gui;

import com.xkball.x3dmap.api.client.runtime.IX3dMapRuntime;
import com.xkball.x3dmap.api.client.viewport.IMapViewport;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.widget.IconCheckBox;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.utils.VanillaUtils;
import net.minecraft.resources.Identifier;

import java.util.List;

@NonNullByDefault
public interface IMapScreenContext {

    Identifier extensionId();

    IX3dMapRuntime runtime();

    IMapViewport viewport();

    IMapGui gui();

    boolean containsState(String key);

    void removeState(String key);

    boolean getBooleanState(String key, boolean defaultValue);

    void setBooleanState(String key, boolean value);

    int getIntState(String key, int defaultValue);

    void setIntState(String key, int value);

    float getFloatState(String key, float defaultValue);

    void setFloatState(String key, float value);

    String getStringState(String key, String defaultValue);

    void setStringState(String key, String value);

    default void addLayerToggle(Identifier layerId, Identifier sprite, String tooltipKey) {
        this.addLayerToggle(layerId, sprite, tooltipKey, layerId);
    }

    default void addLayerToggle(Identifier stateId, Identifier sprite, String tooltipKey, Identifier... layerIds) {
        var layers = this.viewport().layers();
        var controlledLayerIds = List.of(layerIds);
        var stateKey = "layer:" + stateId;
        var visible = this.getBooleanState(stateKey, controlledLayerIds.stream().allMatch(layers::visible));
        for (var layerId : controlledLayerIds) {
            layers.setVisible(layerId, visible);
        }
        var button = new IconCheckBox(VanillaUtils.convertId(sprite));
        button.setValue(visible);
        button.onChange = () -> {
            var value = button.getValue();
            for (var layerId : controlledLayerIds) {
                layers.setVisible(layerId, value);
            }
            this.setBooleanState(stateKey, value);
        };
        button.withTooltip(IComponent.translatable(tooltipKey));
        this.gui().addToolbarWidget(MapToolbarSlot.LEFT, button);
    }
}
