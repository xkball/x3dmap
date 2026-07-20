package com.xkball.x3dmap.api.client.render;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Set;

@NonNullByDefault
public record Map3dLayerSpec(
        Identifier id,
        Set<Identifier> presets,
        Map3dLayerPhase phase,
        int order,
        int inputOrder,
        boolean visibleByDefault
) {

    public Map3dLayerSpec {
        Objects.requireNonNull(id);
        presets = Set.copyOf(presets);
        Objects.requireNonNull(phase);
    }
}
