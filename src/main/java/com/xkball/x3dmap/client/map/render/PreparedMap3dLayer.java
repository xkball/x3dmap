package com.xkball.x3dmap.client.map.render;

import com.xkball.x3dmap.api.client.render.IMap3dRenderCommand;
import com.xkball.x3dmap.api.client.render.Map3dLayerSpec;
import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public record PreparedMap3dLayer(Map3dLayerSpec spec, IMap3dRenderCommand command) {
}
