package com.xkball.x3dmap.api.client.viewport;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

@NonNullByDefault
public interface IMapCameraControl extends AutoCloseable {

    Identifier owner();

    int priority();

    boolean active();

    MapCameraState state();

    void setState(MapCameraState state);

    @Override
    void close();
}
