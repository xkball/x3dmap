package com.xkball.x3dmap.client.map.render;

import com.xkball.x3dmap.api.client.render.IMap2dRenderContext;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.xklib.ui.render.IGUIGraphics;
import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public record Map2dRenderContextImpl(IMapFrame frame, IGUIGraphics graphics) implements IMap2dRenderContext {
}
