package com.xkball.x3dmap.api.client.gui;

import com.xkball.x3dmap.api.client.gui.input.MapInputEvent;
import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public interface IMapScreenExtension extends AutoCloseable {

    default void onOpen() {
    }

    default boolean handle(MapInputEvent event) {
        return false;
    }

    default void tick() {
    }

    @Override
    default void close() {
    }
}
