package com.xkball.x3dmap.client.terrain.render;

import com.xkball.x3dmap.client.terrain.ChunkStorage;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

public class MapNodeModel {
    public final int depth;
    public final int x;
    public final int y;
    public final int z;
    public final Int2ObjectMap<ChunkStorage.TerrainBlockData> data = new Int2ObjectOpenHashMap<>();
    
    public MapNodeModel(ChunkPos pos, int sectionY, MapChunkView view00, MapChunkView view10, MapChunkView view01, MapChunkView view11) {
        this.depth = 5;
        this.x = pos.x() >> 1;
        this.y = sectionY >> 1;
        this.z = pos.z() >> 1;
    }
    
    public MapNodeModel(List<MapNodeModel> subNodes) {
        assert subNodes.size() == 8;
        var subNode000 = subNodes.getFirst();
        this.depth = subNode000.depth + 1;
        this.x = subNode000.x >> 1;
        this.y = subNode000.y >> 1;
        this.z = subNode000.z >> 1;
        
    }
    
}
