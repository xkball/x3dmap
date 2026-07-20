package com.xkball.x3dmap.api.client.render;

import com.xkball.x3dmap.api.client.runtime.IX3dMapRuntime;
import com.xkball.x3dmap.api.client.viewport.IMapViewport;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

@NonNullByDefault
public interface IMapLayerContext {

    Identifier layerId();

    IX3dMapRuntime runtime();

    IMapViewport viewport();

    void invalidate();
}
