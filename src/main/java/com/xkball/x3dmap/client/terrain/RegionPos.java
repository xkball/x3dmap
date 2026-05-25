package com.xkball.x3dmap.client.terrain;

import net.minecraft.world.level.ChunkPos;

public record RegionPos(int x, int z) {
    
    public static RegionPos ofChunk(ChunkPos chunkPos) {
        return new RegionPos(chunkPos.x() >> RegionStorage.REGION_SHIFT, chunkPos.z() >> RegionStorage.REGION_SHIFT);
    }
    
    public ChunkPos toChunkPos() {
        return new ChunkPos(x << RegionStorage.REGION_SHIFT, z << RegionStorage.REGION_SHIFT);
    }
    
    public int getMinX() {
        return toChunkPos().getMinBlockX();
    }
    
    public int getMinZ() {
        return toChunkPos().getMinBlockZ();
    }
}
