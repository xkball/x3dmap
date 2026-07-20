package com.xkball.x3dmap.api.client.viewport;

import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public record MapCameraState(
        float targetX,
        float targetY,
        float targetZ,
        float xRotation,
        float yRotation,
        float distance,
        float fieldOfView
) {
}
