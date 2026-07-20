package com.xkball.x3dmap.api.client.render;

import com.xkball.xklibmc.annotation.NonNullByDefault;

@FunctionalInterface
@NonNullByDefault
public interface IMap2dLayerFactory {

    IMap2dLayer create(IMapLayerContext context);
}
