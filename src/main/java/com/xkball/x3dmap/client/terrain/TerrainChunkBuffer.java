package com.xkball.x3dmap.client.terrain;

import com.xkball.xklibmc.api.client.b3d.ISTD140Writer;
import com.xkball.xklibmc.client.b3d.buffer.ManagedGpuBuffer;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;

import java.util.List;

public class TerrainChunkBuffer {
    
    public ManagedGpuBuffer gpuBuffer;
    public Int2IntLinkedOpenHashMap inChunkMap = new Int2IntLinkedOpenHashMap();
    
    public TerrainChunkBuffer(ManagedGpuBuffer gpuBuffer, List<ABlock> blocks) {
        this.gpuBuffer = gpuBuffer;
        var index = 0;
        while (index < blocks.size()) {
            var aSize = 16*16;
            var index2 = Math.min(index + aSize, blocks.size());
            var actualSize = index2 - index;
            var subList = blocks.subList(index, index2);
            var chunkId = gpuBuffer.put(ISTD140Writer.batchBuildStd140Block(subList));
            this.inChunkMap.put(chunkId, actualSize);
            index += aSize;
        }
    }
}
