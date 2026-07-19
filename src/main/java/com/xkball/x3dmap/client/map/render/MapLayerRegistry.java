package com.xkball.x3dmap.client.map.render;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.api.client.registration.IMapLayerRegistration;
import com.xkball.x3dmap.api.client.render.IMapLayerManager;
import com.xkball.x3dmap.api.client.render.MapRenderTarget;
import com.xkball.x3dmap.api.client.render.PictureInPictureRenderLayer;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@NonNullByDefault
public final class MapLayerRegistry implements IMapLayerRegistration, IMapLayerManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<Identifier, LayerDefinition> definitions = new LinkedHashMap<>();

    @Override
    public void register(Identifier id, Set<MapRenderTarget> targets, Supplier<? extends PictureInPictureRenderLayer<?, ?>> factory) {
        var definition = new LayerDefinition(id, Set.copyOf(targets), factory);
        if (this.definitions.putIfAbsent(id, definition) != null) {
            LOGGER.error("Duplicate map layer registration: {}", id);
            throw new IllegalArgumentException("Duplicate map layer registration: " + id);
        }
    }

    @Override
    public List<Identifier> layers(MapRenderTarget target) {
        var result = new ArrayList<Identifier>();
        for (var definition : this.definitions.values()) {
            if (definition.targets().contains(target)) {
                result.add(definition.id());
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<LayerDefinition> definitions() {
        return List.copyOf(this.definitions.values());
    }

    public record LayerDefinition(
            Identifier id,
            Set<MapRenderTarget> targets,
            Supplier<? extends PictureInPictureRenderLayer<?, ?>> factory
    ) {
    }
}
