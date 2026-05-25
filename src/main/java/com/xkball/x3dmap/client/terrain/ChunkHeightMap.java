package com.xkball.x3dmap.client.terrain;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

public class ChunkHeightMap {
    
    public static final StreamCodec<ByteBuf, ChunkHeightMap> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ChunkHeightMap decode(ByteBuf input) {
            var res = new ChunkHeightMap();
            for (int i = 0; i < 256; i++) {
                res.heightMap[i] = input.readInt();
            }
            for (int i = 0; i < 256; i++) {
                res.color[i] = input.readInt();
            }
            return res;
        }
        
        @Override
        public void encode(ByteBuf output, ChunkHeightMap value) {
            for (int i = 0; i < 256; i++) {
                output.writeInt(value.heightMap[i]);
            }
            for (int i = 0; i < 256; i++) {
                output.writeInt(value.color[i]);
            }
        }
    };
    
    public final int[] heightMap = new int[256];
    public final int[] color = new int[256];
    
    public ChunkHeightMap() {}
    
    public int get(int x, int z) {
        x &= 0xF;
        z &= 0xF;
        return this.heightMap[(x << 4) + z];
    }
    
    public void set(int x, int z, int height) {
        x &= 0xF;
        z &= 0xF;
        this.heightMap[(x << 4) + z] = height;
    }
    
    public void setColor(int x, int z, int color) {
        x &= 0xF;
        z &= 0xF;
        this.color[(x << 4) + z] = color;
    }
    
    public int getColor(int x, int z) {
        x &= 0xF;
        z &= 0xF;
        return this.color[(x << 4) + z];
    }
}
