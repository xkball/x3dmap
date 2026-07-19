package com.xkball.x3dmap.client.map.runtime;

import com.xkball.x3dmap.api.client.runtime.ITerrainView;
import com.xkball.x3dmap.client.terrain.TerrainChunkManager;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.OptionalInt;

@NonNullByDefault
final class TerrainViewImpl implements ITerrainView {

    private final TerrainChunkManager terrainChunkManager;

    TerrainViewImpl(TerrainChunkManager terrainChunkManager) {
        this.terrainChunkManager = terrainChunkManager;
    }

    @Override
    public OptionalInt height(ResourceKey<Level> dimension, int x, int z) {
        var storage = this.terrainChunkManager.storageMap.get(dimension);
        if (storage == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(storage.getHeight(x, z));
    }

    @Override
    public int color(ResourceKey<Level> dimension, int x, int z) {
        var storage = this.terrainChunkManager.storageMap.get(dimension);
        return storage == null ? 0 : storage.getColor(x, z);
    }

    @Override
    public boolean containsChunk(ResourceKey<Level> dimension, ChunkPos chunkPos) {
        var storage = this.terrainChunkManager.storageMap.get(dimension);
        return storage != null && storage.containsChunk(chunkPos);
    }
}
