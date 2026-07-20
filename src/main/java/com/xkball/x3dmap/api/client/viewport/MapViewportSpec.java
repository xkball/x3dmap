package com.xkball.x3dmap.api.client.viewport;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

@NonNullByDefault
public record MapViewportSpec(
        ResourceKey<Level> dimension,
        Identifier preset,
        MapCameraState initialCamera,
        boolean cullNear,
        int lodDistance,
        int minimapHighDetailRange
) {

    public MapViewportSpec {
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(preset);
        Objects.requireNonNull(initialCamera);
    }
}
