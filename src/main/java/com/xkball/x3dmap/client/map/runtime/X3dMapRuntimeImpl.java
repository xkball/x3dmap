package com.xkball.x3dmap.client.map.runtime;

import com.xkball.x3dmap.api.client.render.IMapLayerManager;
import com.xkball.x3dmap.api.client.runtime.ITerrainView;
import com.xkball.x3dmap.api.client.runtime.IX3dMapRuntime;
import com.xkball.x3dmap.api.client.storage.IMapStorageManager;
import com.xkball.x3dmap.api.client.viewport.IMapViewportManager;
import com.xkball.x3dmap.client.map.render.MapLayerRegistry;
import com.xkball.x3dmap.client.map.storage.MapStorageManagerImpl;
import com.xkball.x3dmap.client.map.viewport.MapViewportManagerImpl;
import com.xkball.x3dmap.client.terrain.TerrainChunkManager;
import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public final class X3dMapRuntimeImpl implements IX3dMapRuntime {

    private final MapStorageManagerImpl storage;
    private final TerrainViewImpl terrain;
    private final MapLayerRegistry layers;
    private final MapViewportManagerImpl viewports;

    public X3dMapRuntimeImpl(MapStorageManagerImpl storage, MapLayerRegistry layers, TerrainChunkManager terrainChunkManager) {
        this.storage = storage;
        this.layers = layers;
        this.terrain = new TerrainViewImpl(terrainChunkManager);
        this.viewports = new MapViewportManagerImpl(this);
    }

    @Override
    public IMapStorageManager storage() {
        return this.storage;
    }

    @Override
    public ITerrainView terrain() {
        return this.terrain;
    }

    @Override
    public IMapLayerManager layers() {
        return this.layers;
    }

    @Override
    public IMapViewportManager viewports() {
        return this.viewports;
    }

    public MapStorageManagerImpl storageImpl() {
        return this.storage;
    }

    public MapLayerRegistry layerRegistry() {
        return this.layers;
    }

    public MapViewportManagerImpl viewportManagerImpl() {
        return this.viewports;
    }
}
