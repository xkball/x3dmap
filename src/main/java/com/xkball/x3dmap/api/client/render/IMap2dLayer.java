package com.xkball.x3dmap.api.client.render;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.jspecify.annotations.Nullable;

@NonNullByDefault
public interface IMap2dLayer extends IMapContentLayer {

    @Nullable IMap2dRenderCommand extract(IMapFrame frame);
}
