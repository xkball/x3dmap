package com.xkball.x3dmap.api.client.runtime;

import com.xkball.x3dmap.api.client.render.IMapLayerManager;
import com.xkball.x3dmap.api.client.storage.IMapStorageManager;
import com.xkball.x3dmap.api.client.viewport.IMapViewportManager;
import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public interface IX3dMapRuntime {

    IMapStorageManager storage();

    ITerrainView terrain();

    IMapLayerManager layers();

    IMapViewportManager viewports();
}
