package com.xkball.x3dmap.api.client.viewport;

import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public interface IMapViewportManager {

    IMapViewport create(MapViewportSpec spec);
}
