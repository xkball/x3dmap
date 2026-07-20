package com.xkball.x3dmap.client.map.render;

import com.xkball.x3dmap.api.client.render.IMapLayerContext;
import com.xkball.x3dmap.api.client.runtime.IX3dMapRuntime;
import com.xkball.x3dmap.api.client.viewport.IMapViewport;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

@NonNullByDefault
record MapLayerContextImpl(
        Identifier layerId,
        IX3dMapRuntime runtime,
        IMapViewport viewport
) implements IMapLayerContext {

    @Override
    public void invalidate() {
        this.viewport.invalidate();
    }
}
