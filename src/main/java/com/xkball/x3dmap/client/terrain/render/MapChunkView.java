package com.xkball.x3dmap.client.terrain.render;

import com.xkball.x3dmap.client.terrain.file.FileChunkState;
import com.xkball.x3dmap.client.terrain.file.MapChunk;
import com.xkball.x3dmap.client.terrain.file.MapRegion;

public class MapChunkView implements AutoCloseable{
    
    private final MapRegion parent;
    public final MapChunk chunk;
    
    public MapChunkView(MapRegion parent, MapChunk chunk) {
        this.parent = parent;
        this.chunk = chunk;
    }
    
    @Override
    public void close() throws Exception {
        if(this.chunk.state == FileChunkState.NORMAL){
            this.parent.clearChunkData(this.chunk.chunkPos);
        }
    }
}
