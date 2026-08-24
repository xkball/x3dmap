package com.xkball.x3dmap.client.terrain.file;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Arrays;

@NonNullByDefault
public class MapRegionHeightMap implements IMapFile{
    
    public final boolean[] chunkExists = new boolean[32 * 32];
    public final int[] heightMap = new int[512 * 512];
    public final int[] color = new int[512 * 512];
    private volatile boolean dirty = false;
    
    public MapRegionHeightMap() {
        Arrays.fill(chunkExists, false);
    }
    
    public void setChunk(MapChunk chunk) {
        var cx = chunk.chunkPos.getRegionLocalX();
        var cz = chunk.chunkPos.getRegionLocalZ();
        this.setChunkExists(cx, cz, true);
        for (var x = chunk.chunkPos.getMinBlockX(); x <= chunk.chunkPos.getMaxBlockX(); x++) {
            for (var z = chunk.chunkPos.getMinBlockZ(); z <= chunk.chunkPos.getMaxBlockZ(); z++) {
                this.setHeight(x, z, (int) chunk.aabb.minY);
                this.setColor(x, z, 0);
            }
        }
        chunk.data.forEach((entry, blockData) -> {
            if (entry.y() < this.getHeight(entry.x(), entry.z())) return;
            this.setHeight(entry.x(), entry.z(), entry.y());
            this.setColor(entry.x(), entry.z(), blockData.color());
        });
        this.dirty = true;
    }
    
    public void deleteChunk(ChunkPos chunkPos) {
        var cx = chunkPos.getRegionLocalX();
        var cz = chunkPos.getRegionLocalZ();
        this.setChunkExists(cx, cz, false);
        this.dirty = true;
    }
    
    private void setChunkExists(int cx, int cz, boolean value) {
        cx &= 0x1F;
        cz &= 0x1F;
        this.chunkExists[(cx << 5) + cz] = value;
    }
    
    public boolean chunkExists(int cx, int cz) {
        cx &= 0x1F;
        cz &= 0x1F;
        return this.chunkExists[(cx << 5) + cz];
    }
    
    public int getHeight(int x, int z) {
        x &= 0x1FF;
        z &= 0x1FF;
        return this.heightMap[(x << 9) + z];
    }
    
    public int getColor(int x, int z) {
        x &= 0x1FF;
        z &= 0x1FF;
        return this.color[(x << 9) + z];
    }
    
    private void setHeight(int x, int z, int height) {
        x &= 0x1FF;
        z &= 0x1FF;
        this.heightMap[(x << 9) + z] = height;
    }
    
    private void setColor(int x, int z, int color) {
        x &= 0x1FF;
        z &= 0x1FF;
        this.color[(x << 9) + z] = color;
    }
    
    @Override
    public Path getFile(Path dir) {
        return dir.resolve("heightmap");
    }
    
    @Override
    public boolean dirty() {
        return this.dirty;
    }
    
    @Override
    public void read(RandomAccessFile raf) throws IOException {
        raf.seek(8);
        for (int i = 0; i < 32 * 32; i++) {
            this.chunkExists[i] = raf.readBoolean();
        }
        for (int i = 0; i < 512 * 512; i++) {
            this.heightMap[i] = raf.readInt();
        }
        for (int i = 0; i < 512 * 512; i++) {
            this.color[i] = raf.readInt();
        }
    }
    
    @Override
    public void write(RandomAccessFile raf, @Nullable RandomAccessFile oldFile) throws IOException {
        raf.writeInt(MAGIC);
        raf.writeInt(FILE_VERSION);
        for (int i = 0; i < 32 * 32; i++) {
            raf.writeBoolean(this.chunkExists[i]);
        }
        for (int i = 0; i < 512 * 512; i++) {
            raf.writeInt(this.heightMap[i]);
        }
        for (int i = 0; i < 512 * 512; i++) {
            raf.writeInt(this.color[i]);
        }
    }
}
