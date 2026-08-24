package com.xkball.x3dmap.client.terrain.render;

import com.xkball.x3dmap.client.terrain.file.MapChunk;
import com.xkball.x3dmap.client.terrain.file.MapRegion;
import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public class MapChunkView implements AutoCloseable{
    
    private final MapRegion parent;
    public final MapChunk chunk;
    
    public MapChunkView(MapRegion parent, MapChunk chunk) {
        this.parent = parent;
        this.chunk = chunk;
    }
    
    @Override
    public void close() {
        if(this.chunk.state == MapChunk.MapChunkState.NORMAL){
            this.parent.clearChunkData(this.chunk.chunkPos);
        }
    }
}
