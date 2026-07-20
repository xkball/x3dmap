package com.xkball.x3dmap.api.client.render;

import com.xkball.xklibmc.annotation.NonNullByDefault;

@FunctionalInterface
@NonNullByDefault
public interface IMap3dRenderCommand {

    void render(IMap3dRenderContext context);
}
