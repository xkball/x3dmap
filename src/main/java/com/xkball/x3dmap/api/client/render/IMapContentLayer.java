package com.xkball.x3dmap.api.client.render;

import com.xkball.x3dmap.api.client.gui.input.IMapInputContext;
import com.xkball.x3dmap.api.client.gui.input.MapInputEvent;
import com.xkball.x3dmap.api.client.gui.input.MapInputResult;
import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public interface IMapContentLayer extends AutoCloseable {

    default MapInputResult handle(MapInputEvent event, IMapInputContext context) {
        return MapInputResult.PASS;
    }

    default void tick() {
    }

    @Override
    default void close() {
    }
}
