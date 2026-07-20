package com.xkball.x3dmap.api.client.render;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.jspecify.annotations.Nullable;

@NonNullByDefault
public interface IMap3dLayer extends IMapContentLayer {

    @Nullable IMap3dRenderCommand prepareRender(IMapFrame frame);
}
