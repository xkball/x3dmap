package com.xkball.x3dmap.client.map.render;

import com.xkball.x3dmap.api.client.render.IMap2dRenderCommand;
import com.xkball.x3dmap.api.client.render.Map2dLayerSpec;
import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public record PreparedMap2dLayer(Map2dLayerSpec spec, IMap2dRenderCommand command) {
}
