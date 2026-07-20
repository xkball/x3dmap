package com.xkball.x3dmap.api.client.viewport;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.joml.Vector3f;

@NonNullByDefault
public record MapRay(
        float originX,
        float originY,
        float originZ,
        float directionX,
        float directionY,
        float directionZ
) {

    public Vector3f origin() {
        return new Vector3f(this.originX, this.originY, this.originZ);
    }

    public Vector3f direction() {
        return new Vector3f(this.directionX, this.directionY, this.directionZ);
    }

    public Vector3f point(float distance) {
        return this.direction().mul(distance).add(this.originX, this.originY, this.originZ);
    }
}
