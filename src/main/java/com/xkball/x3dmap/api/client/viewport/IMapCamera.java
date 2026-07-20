package com.xkball.x3dmap.api.client.viewport;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

@NonNullByDefault
public interface IMapCamera {

    MapCameraState state();

    boolean externallyControlled();

    IMapCameraControl acquireControl(Identifier owner, int priority);
}
