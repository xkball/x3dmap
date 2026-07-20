package com.xkball.x3dmap.api.client.render;

import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public enum Map3dLayerPhase {
    BEFORE_TERRAIN,
    TERRAIN,
    AFTER_TERRAIN,
    TRANSLUCENT,
    AFTER_EFFECTS
}
