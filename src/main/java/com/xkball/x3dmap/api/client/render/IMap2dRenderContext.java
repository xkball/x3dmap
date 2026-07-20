package com.xkball.x3dmap.api.client.render;

import com.xkball.xklib.ui.render.IGUIGraphics;
import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public interface IMap2dRenderContext {

    IMapFrame frame();

    IGUIGraphics graphics();
}
