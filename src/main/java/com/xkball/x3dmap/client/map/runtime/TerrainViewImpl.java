package com.xkball.x3dmap.client.map.runtime;

import com.xkball.x3dmap.api.client.runtime.ITerrainView;
import com.xkball.x3dmap.client.terrain.TerrainMapManager;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.world.level.ChunkPos;

import java.util.OptionalInt;

@NonNullByDefault
final class TerrainViewImpl implements ITerrainView {

    private final TerrainMapManager terrainMapManager;

    TerrainViewImpl(TerrainMapManager terrainMapManager) {
        this.terrainMapManager = terrainMapManager;
    }

    @Override
    public OptionalInt height(int x, int z) {
        var storage = this.terrainMapManager.currentChunkStorage;
        if (storage == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(storage.getHeight(x, z));
    }

    @Override
    public int color(int x, int z) {
        var storage = this.terrainMapManager.currentChunkStorage;
        return storage == null ? 0 : storage.getColor(x, z);
    }

    @Override
    public boolean containsChunk(ChunkPos chunkPos) {
        var storage = this.terrainMapManager.currentChunkStorage;
        return storage != null && storage.containsChunk(chunkPos);
    }
}
