package com.xkball.x3dmap.client.terrain;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.world.level.ChunkPos;

@NonNullByDefault
public record RegionPos(int x, int z) {
    
    public static final int REGION_SHIFT = 5;
    public static final int REGION_SIZE = 1 << REGION_SHIFT;
    
    public static RegionPos ofChunk(ChunkPos chunkPos) {
        return new RegionPos(chunkPos.x() >> REGION_SHIFT, chunkPos.z() >> REGION_SHIFT);
    }
    
    public ChunkPos toChunkPos() {
        return new ChunkPos(x << REGION_SHIFT, z << REGION_SHIFT);
    }
    
    public ChunkPos toChunkPos(int dx, int dz){
        return new ChunkPos((x << REGION_SHIFT) + dx, (z << REGION_SHIFT) + dz);
    }
    
    public int getMinX() {
        return toChunkPos().getMinBlockX();
    }
    
    public int getMinZ() {
        return toChunkPos().getMinBlockZ();
    }
    
    @Override
    public String toString() {
        return "("  + x + "," + z + ')';
    }
}
