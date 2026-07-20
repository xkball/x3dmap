package com.xkball.x3dmap.api.client.viewport;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

@NonNullByDefault
public interface IMapProjection {

    @Nullable Vector2f worldToScreen(Vector3fc worldPosition);

    @Nullable MapRay screenRay(double screenX, double screenY);

    @Nullable Vector3f screenToTerrain(double screenX, double screenY);
}
