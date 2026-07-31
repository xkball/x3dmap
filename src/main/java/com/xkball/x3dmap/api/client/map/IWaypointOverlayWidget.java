package com.xkball.x3dmap.api.client.map;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.joml.Vector3fc;

@NonNullByDefault
public interface IWaypointOverlayWidget {

    Vector3fc worldPosition();
}
