package com.xkball.x3dmap.client.terrain;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.utils.CodecUtils;
import com.xkball.x3dmap.utils.VanillaUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RegionStorage {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int REGION_SHIFT = 5;
    public static final int REGION_SIZE = 1 << REGION_SHIFT;
    
    private static final int MAGIC = 0x584B5247;
    private static final int HEADER_SIZE = 16;
    private static final int INDEX_ENTRY_SIZE = 12;
    
    public final AABB aabb;
    public final RegionPos regionPos;
    private final Map<ChunkPos, ChunkStorage> chunkMap = new ConcurrentHashMap<>();
    
    public RegionStorage(RegionPos regionPos, int minHeight, int maxHeight) {
        this.regionPos = regionPos;
        var minX = regionPos.getMinX();
        var minZ = regionPos.getMinZ();
        this.aabb = new AABB(minX, minHeight, minZ, minX + 512, maxHeight, minZ + 512);
    }
    
    public static RegionPos toRegionPos(ChunkPos chunkPos) {
        return RegionPos.ofChunk(chunkPos);
    }
    
    public @Nullable ChunkStorage getChunk(ChunkPos chunkPos) {
        return this.chunkMap.get(chunkPos);
    }
    
    public void putChunk(ChunkStorage chunkStorage) {
        var old = this.chunkMap.put(chunkStorage.chunkPos, chunkStorage);
        if (old != null) {
            old.unloadGpu();
        }
    }
    
    public void deleteChunk(ChunkPos chunkPos) {
        var chunk = this.chunkMap.get(chunkPos);
        if (chunk != null) {
            chunk.unloadGpu();
            chunk.releaseData();
            chunk.heightMap = new ChunkHeightMap();
            chunk.state = ChunkStorage.State.DIRTY;
        }
    }
    
    public boolean containsChunk(ChunkPos chunkPos) {
        var chunk = this.chunkMap.get(chunkPos);
        return chunk != null;
    }
    
    public Collection<ChunkStorage> chunks() {
        return this.chunkMap.values();
    }
    
    public boolean haveDirtyChunk() {
        for (var chunkStorage : this.chunkMap.values()) {
            if (chunkStorage.state == ChunkStorage.State.DIRTY) {
                return true;
            }
        }
        return false;
    }
    
    public boolean haveNoDataChunk() {
        for (var chunkStorage : this.chunkMap.values()) {
            if (chunkStorage.state == ChunkStorage.State.NO_DATA) {
                return true;
            }
        }
        return false;
    }
    
    public Path getFile(Path directory) {
        return directory.resolve(this.regionPos.x() + "," + this.regionPos.z());
    }
    
    public void saveToFile(Path directory, LevelChunkStorage levelStorage) {
        var file = this.getFile(directory).toFile();
        var parentDir = file.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }
        var x0 = this.regionPos.x();
        var z0 = this.regionPos.z();
        LOGGER.info("Saving map at {}, chunk from ({},{}) to ({},{})",
                levelStorage.dimension.identifier(), x0 << REGION_SHIFT, z0 << REGION_SHIFT,
                (x0 << REGION_SHIFT) + REGION_SIZE, (z0 << REGION_SHIFT) + REGION_SIZE);
        
        long[] oldOffsets = null;
        int[] oldLengths = null;
        if (file.exists()) {
            oldOffsets = new long[REGION_SIZE * REGION_SIZE];
            oldLengths = new int[REGION_SIZE * REGION_SIZE];
            try (var inRaf = new RandomAccessFile(file, "r")) {
                int magic = inRaf.readInt();
                if (magic == MAGIC) {
                    int version = inRaf.readInt();
                    if (version == LevelChunkStorage.VERSION) {
                        inRaf.readInt();
                        inRaf.readInt();
                        readIndex(inRaf, oldOffsets, oldLengths);
                    }
                }
            } catch (IOException ignored) {
                oldOffsets = null;
            }
        }
        
        var tempFile = new File(file.getAbsolutePath() + ".tmp");
        try (var outRaf = new RandomAccessFile(tempFile, "rw")) {
            outRaf.setLength(0);
            writeFileHeader(outRaf);
            writeEmptyIndex(outRaf);
            
            for (int dx = 0; dx < REGION_SIZE; dx++) {
                for (int dz = 0; dz < REGION_SIZE; dz++) {
                    var pos = new ChunkPos((this.regionPos.x() << REGION_SHIFT) + dx,
                            (this.regionPos.z() << REGION_SHIFT) + dz);
                    var chunk = this.chunkMap.get(pos);
                    if (chunk == null) continue;
                    if (chunk.state == ChunkStorage.State.DIRTY) {
                        writeChunkBlob(outRaf, chunk);
                    } else {
                        copyOrSerialize(outRaf, file, pos, chunk, oldOffsets, oldLengths);
                    }
                }
            }
            
        } catch (IOException e) {
            LOGGER.error("Failed to save region file {}", file.getAbsolutePath(), e);
            throw new RuntimeException(e);
        }
        
        try {
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.error("Failed to move temp file {}", file.getAbsolutePath(), e);
            throw new RuntimeException(e);
        } finally {
            tempFile.delete();
        }
        for (ChunkStorage chunk : this.chunkMap.values()) {
            if (chunk.state == ChunkStorage.State.DIRTY) {
                chunk.state = ChunkStorage.State.ON_BOTH_SIDE;
            }
            if (chunk.state == ChunkStorage.State.ON_BOTH_SIDE) {
                chunk.releaseData();
                chunk.state = ChunkStorage.State.ONLY_ON_GPU;
            }
        }
    }
    
    private void copyOrSerialize(RandomAccessFile outRaf, File oldFile, ChunkPos pos,
            ChunkStorage chunk, long[] oldOffsets, int[] oldLengths) throws IOException {
        if (oldOffsets != null) {
            int idx = indexOf(pos);
            long oldOff = oldOffsets[idx];
            int oldLen = oldLengths[idx];
            if (oldOff > 0 && oldLen > 0) {
                byte[] buf = new byte[oldLen];
                try (var inRaf = new RandomAccessFile(oldFile, "r")) {
                    inRaf.seek(oldOff);
                    inRaf.readFully(buf);
                }
                outRaf.seek(outRaf.length());
                long newOff = outRaf.getFilePointer();
                outRaf.write(buf);
                updateIndexEntry(outRaf, pos, newOff, oldLen);
            }
        } else {
            if (chunk.state == ChunkStorage.State.ONLY_ON_GPU) {
                LOGGER.error("Serializing a chunk data only on gpu.This should not happen! {}", chunk.chunkPos);
            }
            writeChunkBlob(outRaf, chunk);
        }
    }
    
    private void writeChunkBlob(RandomAccessFile raf, ChunkStorage chunk) throws IOException {
        byte[] compressed = writeChunkBlobBytes(chunk);
        long offset = raf.getFilePointer();
        raf.write(compressed);
        updateIndexEntry(raf, chunk.chunkPos, offset, compressed.length);
    }
    
    private byte[] writeChunkBlobBytes(ChunkStorage chunk) {
        var byteBuf = Unpooled.buffer();
        ChunkPos.STREAM_CODEC.encode(byteBuf, chunk.chunkPos);
        CodecUtils.AABB_STREAM_CODEC.encode(byteBuf, chunk.aabb);
        ChunkHeightMap.STREAM_CODEC.encode(byteBuf, chunk.heightMap);
        ChunkStorage.ChunkStorageData.STREAM_CODEC.encode(byteBuf, chunk.data);
        return VanillaUtils.gzip(byteBuf.array(), 0, byteBuf.readableBytes());
    }
    
    public static @Nullable RegionStorage loadFromFile(Path path, LevelChunkStorage levelStorage) {
        var fileName = path.getFileName().toString();
        var n = fileName.split(",");
        if (n.length != 2) {
            return null;
        }
        int x;
        int z;
        try {
            x = Integer.parseInt(n[0]);
            z = Integer.parseInt(n[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        LOGGER.info("Loading map at {}, chunk from ({},{}) to ({},{})",
                levelStorage.dimension.identifier(), x << REGION_SHIFT, z << REGION_SHIFT,
                (x << REGION_SHIFT) + REGION_SIZE, (z << REGION_SHIFT) + REGION_SIZE);
        try {
            long[] offsets = new long[REGION_SIZE * REGION_SIZE];
            int[] lengths = new int[REGION_SIZE * REGION_SIZE];
            try (var raf = new RandomAccessFile(path.toFile(), "r")) {
                int magic = raf.readInt();
                if (magic != MAGIC) {
                    LOGGER.error("Invalid magic number in region file {}", path);
                    return null;
                }
                int version = raf.readInt();
                if (version != LevelChunkStorage.VERSION) {
                    LOGGER.error("Version mismatch in region file {}: expected {}, got {}",
                            path, LevelChunkStorage.VERSION, version);
                    return null;
                }
                int fileRegionX = raf.readInt();
                int fileRegionZ = raf.readInt();
                if (fileRegionX != x || fileRegionZ != z) {
                    LOGGER.error("Region position mismatch in file {}: header ({},{}), filename ({},{})",
                            path, fileRegionX, fileRegionZ, x, z);
                    return null;
                }
                readIndex(raf, offsets, lengths);
            }
            
            var regionStorage = new RegionStorage(new RegionPos(x, z),
                    levelStorage.minHeight, levelStorage.maxHeight);
            try (var raf = new RandomAccessFile(path.toFile(), "r")) {
                for (int dx = 0; dx < REGION_SIZE; dx++) {
                    for (int dz = 0; dz < REGION_SIZE; dz++) {
                        int idx = dx * REGION_SIZE + dz;
                        long off = offsets[idx];
                        int len = lengths[idx];
                        if (off <= 0 || len <= 0) {
                            continue;
                        }
                        var chunkPos = new ChunkPos((x << REGION_SHIFT) + dx, (z << REGION_SHIFT) + dz);
                        byte[] blob;
                        raf.seek(off);
                        blob = new byte[len];
                        raf.readFully(blob);
                        blob = VanillaUtils.unGzip(blob);
                        var byteBuf = Unpooled.buffer(blob.length);
                        byteBuf.writeBytes(blob);
                        var decodedPos = ChunkPos.STREAM_CODEC.decode(byteBuf);
                        var aabb = CodecUtils.AABB_STREAM_CODEC.decode(byteBuf);
                        var heightMap = ChunkHeightMap.STREAM_CODEC.decode(byteBuf);
                        var data = ChunkStorage.ChunkStorageData.STREAM_CODEC.decode(byteBuf);
                        assert decodedPos.equals(chunkPos);
                        var storage = new ChunkStorage(chunkPos, levelStorage);
                        storage.aabb = aabb;
                        storage.heightMap = heightMap;
                        storage.writeData(data.data());
                        storage.state = ChunkStorage.State.ONLY_ON_MEM;
                        regionStorage.chunkMap.put(chunkPos, storage);
                    }
                }
            }
            return regionStorage;
        } catch (Exception e) {
            LOGGER.error("Failed to load region file {}", path, e);
            return null;
        }
    }
    
    private void writeFileHeader(RandomAccessFile raf) throws IOException {
        raf.writeInt(MAGIC);
        raf.writeInt(LevelChunkStorage.VERSION);
        raf.writeInt(this.regionPos.x());
        raf.writeInt(this.regionPos.z());
    }
    
    private static void writeEmptyIndex(RandomAccessFile raf) throws IOException {
        for (int i = 0; i < REGION_SIZE * REGION_SIZE; i++) {
            raf.writeLong(0L);
            raf.writeInt(0);
        }
    }
    
    private static void readIndex(RandomAccessFile raf, long[] offsets, int[] lengths) throws IOException {
        raf.seek(HEADER_SIZE);
        for (int i = 0; i < REGION_SIZE * REGION_SIZE; i++) {
            offsets[i] = raf.readLong();
            lengths[i] = raf.readInt();
        }
    }
    
    private static void updateIndexEntry(RandomAccessFile raf, ChunkPos pos, long offset, int length)
            throws IOException {
        int idx = indexOf(pos);
        long entryPos = HEADER_SIZE + (long) idx * INDEX_ENTRY_SIZE;
        var p = raf.getFilePointer();
        raf.seek(entryPos);
        raf.writeLong(offset);
        raf.writeInt(length);
        raf.seek(p);
    }
    
    private static int indexOf(ChunkPos pos) {
        int dx = pos.x() & (REGION_SIZE - 1);
        int dz = pos.z() & (REGION_SIZE - 1);
        return dx * REGION_SIZE + dz;
    }
}
