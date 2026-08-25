package com.xkball.x3dmap.client.terrain.file;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@NonNullByDefault
public class MapRegionHeightMap implements IMapFile{
    
    public final boolean[] chunkExists = new boolean[32 * 32];
    public final int[] heightMap = new int[512 * 512];
    public final int[] color = new int[512 * 512];
    private volatile boolean dirty = false;

    public void setChunk(MapChunk chunk) {
        var chunkPos = chunk.chunkPos;
        this.chunkExists[chunkIndex(chunkPos.getRegionLocalX(), chunkPos.getRegionLocalZ())] = true;
        for (var x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            var start = mapIndex(x, chunkPos.getMinBlockZ());
            var end = mapIndex(x, chunkPos.getMaxBlockZ()) + 1;
            Arrays.fill(this.heightMap, start, end, (int) chunk.aabb.minY);
            Arrays.fill(this.color, start, end, 0);
        }
        chunk.data.forEach((entry, blockData) -> {
            var index = mapIndex(entry.x(), entry.z());
            if (entry.y() < this.heightMap[index]) return;
            this.heightMap[index] = entry.y();
            this.color[index] = blockData.color();
        });
        this.dirty = true;
    }

    public void deleteChunk(ChunkPos chunkPos) {
        this.chunkExists[chunkIndex(chunkPos.getRegionLocalX(), chunkPos.getRegionLocalZ())] = false;
        this.dirty = true;
    }

    public boolean chunkExists(int cx, int cz) {
        return this.chunkExists[chunkIndex(cx, cz)];
    }

    public int getHeight(int x, int z) {
        return this.heightMap[mapIndex(x, z)];
    }

    public int getColor(int x, int z) {
        return this.color[mapIndex(x, z)];
    }

    public int[] getChunkColors() {
        var result = new int[32 * 32];
        for (var cx = 0; cx < 32; cx++) {
            for (var cz = 0; cz < 32; cz++) {
                result[chunkIndex(cx, cz)] = this.getChunkColor(cx, cz);
            }
        }
        return result;
    }
    
    public int getChunkColor(int cx, int cz) {
        if (!this.chunkExists[chunkIndex(cx, cz)]) return 0;
        var alpha = 0;
        var red = 0;
        var green = 0;
        var blue = 0;
        for (var x = cx << 4; x < (cx + 1) << 4; x++) {
            for (var z = cz << 4; z < (cz + 1) << 4; z++) {
                var value = this.color[mapIndex(x, z)];
                alpha += value >>> 24;
                red += value >> 16 & 0xFF;
                green += value >> 8 & 0xFF;
                blue += value & 0xFF;
            }
        }
        return alpha / 256 << 24 | red / 256 << 16 | green / 256 << 8 | blue / 256;
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
        raf.seek(Integer.BYTES * 2L);
        var compressedLength = raf.readInt();
        var compressed = new byte[compressedLength];
        raf.readFully(compressed);
        try (var input = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(compressed)))) {
            this.readData(input);
        }
    }

    @Override
    public void write(RandomAccessFile raf, @Nullable RandomAccessFile oldFile) throws IOException {
        var compressed = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(compressed);
             var output = new DataOutputStream(gzip)) {
            this.writeData(output);
        }
        var data = compressed.toByteArray();
        raf.setLength(0);
        raf.writeInt(MAGIC);
        raf.writeInt(FILE_VERSION);
        raf.writeInt(data.length);
        raf.write(data);
    }

    @Override
    public void afterRead() {
        this.dirty = false;
    }

    @Override
    public void afterWrite() {
        this.dirty = false;
    }

    private void readData(DataInput input) throws IOException {
        var data = new byte[32 * 32 + 512 * 512 * 4 * 2];
        var be = new int[512 * 512];
        input.readFully(data);
        var intBE = ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN);
        var intNative = ValueLayout.JAVA_INT;
        MemorySegment.copy(MemorySegment.ofArray(data),32 * 32,MemorySegment.ofArray(be),0, 512 * 512 * 4);
        MemorySegment.copy(MemorySegment.ofArray(be), intBE, 0, MemorySegment.ofArray(this.heightMap), intNative, 0, 512 * 512);
        MemorySegment.copy(MemorySegment.ofArray(data),32 * 32 + 512 * 512 * 4,MemorySegment.ofArray(be),0, 512 * 512 * 4);
        MemorySegment.copy(MemorySegment.ofArray(be), intBE, 0, MemorySegment.ofArray(this.color), intNative, 0, 512 * 512);
        for (int i = 0; i < 32 * 32; i++) {
            this.chunkExists[i] = data[i] != 0;
        }
  
    }

    private void writeData(DataOutput output) throws IOException {
        for (boolean exists : this.chunkExists) {
            output.writeBoolean(exists);
        }
        for (int height : this.heightMap) {
            output.writeInt(height);
        }
        for (int value : this.color) {
            output.writeInt(value);
        }
    }

    private static int chunkIndex(int x, int z) {
        return ((x & 0x1F) << 5) | (z & 0x1F);
    }

    private static int mapIndex(int x, int z) {
        return ((x & 0x1FF) << 9) | (z & 0x1FF);
    }
}
