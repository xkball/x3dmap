package com.xkball.x3dmap.api.client.map;

import com.xkball.x3dmap.client.terrain.TerrainChunkManager;

public record WorldMapExtensionContext(TerrainChunkManager terrainChunkManager, WorldMapExtensionRegistry registry) {
}
