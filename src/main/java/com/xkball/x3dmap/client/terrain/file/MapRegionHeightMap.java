package com.xkball.x3dmap.client.terrain.file;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class MapRegionHeightMap {
    
    public static final StreamCodec<ByteBuf, MapRegionHeightMap> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MapRegionHeightMap decode(ByteBuf input) {
            var res = new MapRegionHeightMap();
            for (int i = 0; i < 512 * 512; i++) {
                res.heightMap[i] = input.readInt();
            }
            for (int i = 0; i < 512 * 512; i++) {
                res.color[i] = input.readInt();
            }
            return res;
        }
        
        @Override
        public void encode(ByteBuf output, MapRegionHeightMap value) {
            for (int i = 0; i < 512 * 512; i++) {
                output.writeInt(value.heightMap[i]);
            }
            for (int i = 0; i < 512 * 512; i++) {
                output.writeInt(value.color[i]);
            }
        }
    };
    
    public final int[] heightMap = new int[512 * 512];
    public final int[] color = new int[512 * 512];
    
    public int getHeight(int x, int z) {
        x &= 0x1FF;
        z &= 0x1FF;
        return this.heightMap[(x << 9) + z];
    }
    
    public void setHeight(int x, int z, int height) {
        x &= 0x1FF;
        z &= 0x1FF;
        this.heightMap[(x << 9) + z] = height;
    }
    
    public void setColor(int x, int z, int color) {
        x &= 0x1FF;
        z &= 0x1FF;
        this.color[(x << 9) + z] = color;
    }
    
    public int getColor(int x, int z) {
        x &= 0x1FF;
        z &= 0x1FF;
        return this.color[(x << 9) + z];
    }
    
    public void read(DataInput input) throws IOException {
        for (int i = 0; i < 512 * 512; i++) {
            this.heightMap[i] = input.readInt();
        }
        for (int i = 0; i < 512 * 512; i++) {
            this.color[i] = input.readInt();
        }
    }
    
    public void write(DataOutput output) throws IOException {
        for (int i = 0; i < 512 * 512; i++) {
            output.writeInt(this.heightMap[i]);
        }
        for (int i = 0; i < 512 * 512; i++) {
            output.writeInt(this.color[i]);
        }
    }
}
