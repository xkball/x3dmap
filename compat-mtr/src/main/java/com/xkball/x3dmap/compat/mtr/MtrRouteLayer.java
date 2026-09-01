package com.xkball.x3dmap.compat.mtr;

import com.xkball.x3dmap.api.client.render.IMap3dLayer;
import com.xkball.x3dmap.api.client.render.IMap3dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.x3dmap.api.client.render.IMapLayerContext;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.jspecify.annotations.Nullable;

@NonNullByDefault
public final class MtrRouteLayer implements IMap3dLayer {

    public MtrRouteLayer(IMapLayerContext layerContext) {
    }

    @Override
    public @Nullable IMap3dRenderCommand prepareRender(IMapFrame frame) {
        return null;
    }
}
