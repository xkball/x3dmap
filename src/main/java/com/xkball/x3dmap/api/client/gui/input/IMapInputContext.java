package com.xkball.x3dmap.api.client.gui.input;

import com.xkball.x3dmap.api.client.viewport.IMapViewport;
import com.xkball.x3dmap.api.client.viewport.MapRay;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

@NonNullByDefault
public interface IMapInputContext {

    IMapViewport viewport();

    @Nullable MapRay screenRay();

    @Nullable Vector3f terrainHit();

    void invalidate();
}
