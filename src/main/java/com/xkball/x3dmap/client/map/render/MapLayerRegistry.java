package com.xkball.x3dmap.client.map.render;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.api.client.registration.IMapLayerRegistration;
import com.xkball.x3dmap.api.client.render.IMap2dLayerFactory;
import com.xkball.x3dmap.api.client.render.IMap3dLayerFactory;
import com.xkball.x3dmap.api.client.render.IMapLayerManager;
import com.xkball.x3dmap.api.client.render.Map2dLayerSpec;
import com.xkball.x3dmap.api.client.render.Map3dLayerSpec;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@NonNullByDefault
public final class MapLayerRegistry implements IMapLayerRegistration, IMapLayerManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Comparator<ThreeDimensionalDefinition> THREE_DIMENSIONAL_ORDER = Comparator
            .comparing((ThreeDimensionalDefinition definition) -> definition.spec().phase())
            .thenComparingInt(definition -> definition.spec().order())
            .thenComparing(definition -> definition.spec().id().toString());
    private static final Comparator<TwoDimensionalDefinition> TWO_DIMENSIONAL_ORDER = Comparator
            .comparing((TwoDimensionalDefinition definition) -> definition.spec().phase())
            .thenComparingInt(definition -> definition.spec().order())
            .thenComparing(definition -> definition.spec().id().toString());
    private final Map<Identifier, ThreeDimensionalDefinition> threeDimensionalDefinitions = new LinkedHashMap<>();
    private final Map<Identifier, TwoDimensionalDefinition> twoDimensionalDefinitions = new LinkedHashMap<>();

    @Override
    public void add3d(Map3dLayerSpec spec, IMap3dLayerFactory factory) {
        this.checkDuplicate(spec.id());
        this.threeDimensionalDefinitions.put(spec.id(), new ThreeDimensionalDefinition(spec, factory));
    }

    @Override
    public void add2d(Map2dLayerSpec spec, IMap2dLayerFactory factory) {
        this.checkDuplicate(spec.id());
        this.twoDimensionalDefinitions.put(spec.id(), new TwoDimensionalDefinition(spec, factory));
    }

    @Override
    public List<Map3dLayerSpec> threeDimensionalLayers(Identifier preset) {
        var result = new ArrayList<Map3dLayerSpec>();
        for (var definition : this.threeDimensionalDefinitions(preset)) {
            result.add(definition.spec());
        }
        return List.copyOf(result);
    }

    @Override
    public List<Map2dLayerSpec> twoDimensionalLayers(Identifier preset) {
        var result = new ArrayList<Map2dLayerSpec>();
        for (var definition : this.twoDimensionalDefinitions(preset)) {
            result.add(definition.spec());
        }
        return List.copyOf(result);
    }

    public List<ThreeDimensionalDefinition> threeDimensionalDefinitions(Identifier preset) {
        return this.threeDimensionalDefinitions.values().stream()
                .filter(definition -> definition.spec().presets().contains(preset))
                .sorted(THREE_DIMENSIONAL_ORDER)
                .toList();
    }

    public List<TwoDimensionalDefinition> twoDimensionalDefinitions(Identifier preset) {
        return this.twoDimensionalDefinitions.values().stream()
                .filter(definition -> definition.spec().presets().contains(preset))
                .sorted(TWO_DIMENSIONAL_ORDER)
                .toList();
    }

    private void checkDuplicate(Identifier id) {
        if (this.threeDimensionalDefinitions.containsKey(id) || this.twoDimensionalDefinitions.containsKey(id)) {
            LOGGER.error("Duplicate map layer registration: {}", id);
            throw new IllegalArgumentException("Duplicate map layer registration: " + id);
        }
    }

    public record ThreeDimensionalDefinition(Map3dLayerSpec spec, IMap3dLayerFactory factory) {
    }

    public record TwoDimensionalDefinition(Map2dLayerSpec spec, IMap2dLayerFactory factory) {
    }
}
