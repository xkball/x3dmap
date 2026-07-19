package com.xkball.x3dmap.client.map.gui;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.api.client.gui.IMapScreenExtensionFactory;
import com.xkball.x3dmap.api.client.registration.IMapGuiRegistration;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@NonNullByDefault
public final class MapGuiRegistry implements IMapGuiRegistration {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final List<ScreenExtensionDefinition> definitions = new ArrayList<>();

    @Override
    public void addScreenExtension(Identifier id, int order, IMapScreenExtensionFactory factory) {
        for (var definition : this.definitions) {
            if (definition.id().equals(id)) {
                LOGGER.error("Duplicate map screen extension registration: {}", id);
                throw new IllegalArgumentException("Duplicate map screen extension registration: " + id);
            }
        }
        this.definitions.add(new ScreenExtensionDefinition(id, order, factory));
        this.definitions.sort(Comparator.comparingInt(ScreenExtensionDefinition::order));
    }

    public List<ScreenExtensionDefinition> definitions() {
        return List.copyOf(this.definitions);
    }

    public record ScreenExtensionDefinition(Identifier id, int order, IMapScreenExtensionFactory factory) {
    }
}
