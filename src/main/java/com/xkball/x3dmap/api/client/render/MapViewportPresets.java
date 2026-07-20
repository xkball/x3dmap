package com.xkball.x3dmap.api.client.render;

import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

@NonNullByDefault
public final class MapViewportPresets {

    public static final Identifier WORLD_MAP = VanillaUtils.modRL("world_map");
    public static final Identifier MINIMAP = VanillaUtils.modRL("minimap");

    private MapViewportPresets() {
    }
}
