package com.xkball.x3dmap.api.client.render;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Set;

@NonNullByDefault
public record Map2dLayerSpec(
        Identifier id,
        Set<Identifier> presets,
        Map2dLayerPhase phase,
        int order,
        int inputOrder,
        boolean visibleByDefault
) {

    public Map2dLayerSpec {
        Objects.requireNonNull(id);
        presets = Set.copyOf(presets);
        Objects.requireNonNull(phase);
    }
}
