package com.xkball.x3dmap.client.terrain.file;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.X3dMapClient;
import com.xkball.x3dmap.client.terrain.RegionPos;
import com.xkball.x3dmap.client.terrain.render.MapChunkView;
import com.xkball.x3dmap.utils.ExpiringResourceCache;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@NonNullByDefault
public class MapRegion implements AutoCloseable{
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAGIC = 0x584B5247;
    public static final int FILE_VERSION = 4;
    private static final long HEADER_SIZE = 16 + 512 * 512 * 4 * 2;
    
    public final RegionPos regionPos;
    private final Identifier level;
    private final Path dir;
    private final Path file;
    private final MapRegionHeightMap heightMap = new MapRegionHeightMap();
    private final @Nullable MapChunk[] chunks = new MapChunk[32 * 32];
    private boolean dirty;
    
    //todo: 区块在拿到一次后才会过期. -> 现在暂时不过期
//    private final ExpiringResourceCache<ChunkPos, MapChunkView> chunkViewCache = ExpiringResourceCache.<ChunkPos, MapChunkView>builder()
//            .loader(this::createChunkView)
//            .loadOn(X3dMapClient.taskExecutor)
////            .expireAfterRead(60)
//            .build();

    private final Map<ChunkPos, MapChunkView> chunkViews = new ConcurrentHashMap<>();
    
    public MapRegion(Identifier level, RegionPos regionPos, Path dir) {
        this.regionPos = regionPos;
        this.level = level;
        this.dir = dir;
        this.file = dir.resolve(this.regionPos.x() + "," + this.regionPos.z());
    }
    
    public void setChunk(MapChunk chunk) {
        var idx = this.getChunkIndex(chunk.chunkPos);
//        this.chunkViewCache.remove(chunk.chunkPos);
        chunk.state = MapChunk.MapChunkState.DIRTY;
        this.chunks[idx] = chunk;
        this.dirty = true;
        for (var x = chunk.chunkPos.getMinBlockX(); x <= chunk.chunkPos.getMaxBlockX(); x++) {
            for (var z = chunk.chunkPos.getMinBlockZ(); z <= chunk.chunkPos.getMaxBlockZ(); z++) {
                this.heightMap.setHeight(x, z, (int) chunk.aabb.minY);
                this.heightMap.setColor(x, z, 0);
            }
        }
        chunk.data.forEach((entry, blockData) -> {
            if (entry.y() < this.heightMap.getHeight(entry.x(), entry.z())) return;
            this.heightMap.setHeight(entry.x(), entry.z(), entry.y());
            this.heightMap.setColor(entry.x(), entry.z(), blockData.color());
        });
    }

    public boolean containsChunk(ChunkPos chunkPos) {
        var chunk = this.chunks[this.getChunkIndex(chunkPos)];
        return chunk != null && chunk.state != MapChunk.MapChunkState.EMPTY;
    }

    public List<MapChunk> getChunks() {
        var result = new ArrayList<MapChunk>();
        for (var chunk : this.chunks) {
            if (chunk != null && chunk.state != MapChunk.MapChunkState.EMPTY) {
                result.add(chunk);
            }
        }
        return result;
    }

    public int getHeight(int x, int z) {
        return this.heightMap.getHeight(x, z);
    }

    public int getColor(int x, int z) {
        return this.heightMap.getColor(x, z);
    }

    public void deleteChunk(ChunkPos chunkPos) {
//        this.chunkViewCache.remove(chunkPos);
        this.chunks[this.getChunkIndex(chunkPos)] = null;
        this.dirty = true;
        for (var x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (var z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                this.heightMap.setHeight(x, z, 0);
                this.heightMap.setColor(x, z, 0);
            }
        }
    }
    
    public void clearChunkData(ChunkPos chunkPos){
        var idx = this.getChunkIndex(chunkPos);
        var chunk = this.chunks[idx];
        if(chunk == null || chunk.state != MapChunk.MapChunkState.NORMAL) return;
        this.chunks[idx] = new MapChunk(chunkPos);
    }
    
    private MapChunkView createChunkView(ChunkPos chunkPos){
        var idx = this.getChunkIndex(chunkPos);
        var chunk = this.chunks[idx];
        if(chunk != null && chunk.state != MapChunk.MapChunkState.EMPTY) return new MapChunkView(this, chunk);
        if (chunk != null && this.file.toFile().exists()) this.readMapChunk(chunkPos);
        chunk = this.chunks[idx];
        if (chunk == null) {
            chunk = new MapChunk(chunkPos);
            this.chunks[idx] = chunk;
        }
        return new MapChunkView(this, chunk);
    }
    
    public CompletableFuture<MapChunkView> getMapChunkView(ChunkPos chunkPos){
//        return this.chunkViewCache.getAsync(chunkPos);
        return CompletableFuture.supplyAsync(() -> this.createChunkView(chunkPos), X3dMapClient.taskExecutor);
    }
    
    public CompletableFuture<List<MapChunkView>> getMapChunkViews(List<ChunkPos> chunkPosList){
//        return this.chunkViewCache.getListAsync(chunkPosList);
        List<MapChunkView> list = new ArrayList<>();
        for (ChunkPos chunkPos : chunkPosList) {
            MapChunkView chunkView = chunkViews.computeIfAbsent(chunkPos, this::createChunkView);
            list.add(chunkView);
        }
        return CompletableFuture.completedFuture(list);
    }
    
    public int getChunkIndex(ChunkPos chunkPos){
        var chunkPos0 = this.regionPos.toChunkPos();
        var dx = chunkPos.x() - chunkPos0.x();
        var dz = chunkPos.z() - chunkPos0.z();
        if(dx < 0 || dz < 0 ||dx >= 32 || dz >= 32) throw new IllegalArgumentException("Chunk pos not belongs region " + this.regionPos);
        return dx * 32 + dz;
    }
    
    public synchronized void readMapChunk(ChunkPos chunkPos) {
        var idx = this.getChunkIndex(chunkPos);
        var old = chunks[idx];
        if(old != null && old.state == MapChunk.MapChunkState.DIRTY) return;
        LOGGER.trace("Reading map single chunk {}/{}/{}", this.level, this.regionPos, chunkPos);
        try (var raf = new RandomAccessFile(this.file.toFile(), "r")) {
            var l = raf.length();
            var p = HEADER_SIZE + idx * 12L;
            if(l < p) return;
            raf.seek(p);
            var offset = raf.readLong();
            var len = raf.readInt();
            chunks[idx] = readMapChunkInternal(raf, offset, len);
        } catch(Exception e){
            LOGGER.error("Failed to read chunk {}", chunkPos, e);
        }
    }
    
    public synchronized void load(){
        if(!this.file.toFile().exists()) return;
        LOGGER.info("Loading map at {}, region {}", this.level, this.regionPos);
        var delete = false;
        try (var raf = new RandomAccessFile(this.file.toFile(), "r")) {
            var magic = raf.readInt();
            var version = raf.readInt();
            var rx = raf.readInt();
            var rz = raf.readInt();
            if(magic != MAGIC || version != FILE_VERSION || rx != this.regionPos.x() || rz != this.regionPos.z()) {
                LOGGER.warn("Invalid map file at {}, mismatch magic number or file version or region pos {}:{}, {}:{}, {}:({},{})",this.regionPos, MAGIC, magic, FILE_VERSION, version, this.regionPos, rx, rz);
                delete = true;
            }
            else {
                this.heightMap.read(raf);
                long[] offsets = new long[32 * 32];
                int[] lengths = new int[32 * 32];
                for (int i = 0; i < 32 * 32; i++) {
                    offsets[i] = raf.readLong();
                    lengths[i] = raf.readInt();
                }
                
                for (int dx = 0; dx < 32; dx++) {
                    for (int dz = 0; dz < 32; dz++) {
                        var idx = dx * 32 + dz;
                        chunks[idx] = readMapChunkInternal(raf, offsets[idx], lengths[idx]);
                    }
                }
            }
        } catch(Exception e){
            LOGGER.error("Failed to load Region {}",this.regionPos, e);
            delete = true;
        }
        if(delete) {
            this.file.toFile().delete();
        }
    }
    
    private @Nullable MapChunk readMapChunkInternal(RandomAccessFile raf, long offset, int len) throws IOException{
        if(offset < 0) return null;
        raf.seek(offset);
        var bufGZip = new byte[len];
        raf.readFully(bufGZip);
        var buf = VanillaUtils.unGzip(bufGZip);
        var byteBuf = Unpooled.buffer(buf.length);
        byteBuf.writeBytes(buf);
        return MapChunk.STREAM_CODEC.decode(byteBuf);
    }
    
    public synchronized void save(){
        if(!dir.toFile().exists()){
            //noinspection ResultOfMethodCallIgnored
            dir.toFile().mkdirs();
        }
        LOGGER.info("Saving map at {}, region {}", this.level, this.regionPos);
        var tempFile = new File(this.file.toFile().getAbsolutePath() + ".tmp");
        if(!this.file.toFile().exists()){
            try (var raf = new RandomAccessFile(tempFile, "rw")){
                raf.setLength(0);
                this.writeHeader(raf);
                var offset = HEADER_SIZE + 32 * 32 * 12L;
                for(int i = 0; i < 32 * 32; i++) {
                    var chunk = this.chunks[i];
                    if(chunk != null && chunk.state != MapChunk.MapChunkState.EMPTY) {
                        if(chunk.state == MapChunk.MapChunkState.DIRTY){
                            chunk.state = MapChunk.MapChunkState.NORMAL;
                        }
                        var byteBuf = Unpooled.buffer();
                        MapChunk.STREAM_CODEC.encode(byteBuf, chunk);
                        var data = VanillaUtils.gzip(byteBuf.array(), 0, byteBuf.readableBytes());
                        this.writeMapChunkInternal(raf, i, offset, data);
                        offset += data.length;
                    }
                    else {
                        raf.seek(HEADER_SIZE + i * 12);
                        raf.writeLong(-1);
                        raf.writeInt(0);
                    }
                    
                }
            } catch (Exception e){
                LOGGER.error("Failed to save region file {}", tempFile, e);
                return;
            }
        }
        else {
            try (var raf = new RandomAccessFile(tempFile, "rw");
                 var in = new RandomAccessFile(this.file.toFile(), "r")) {
                raf.setLength(0);
                this.writeHeader(raf);
                var offset = HEADER_SIZE + 32 * 32 * 12L;
                for(int i = 0; i < 32 * 32; i++) {
                    var chunk = this.chunks[i];
                    if(chunk != null) {
                        if(chunk.state != MapChunk.MapChunkState.EMPTY) {
                            chunk.state = MapChunk.MapChunkState.NORMAL;
                            var byteBuf = Unpooled.buffer();
                            MapChunk.STREAM_CODEC.encode(byteBuf, chunk);
                            var data = VanillaUtils.gzip(byteBuf.array(), 0, byteBuf.readableBytes());
                            this.writeMapChunkInternal(raf, i, offset, data);
                            offset += data.length;
                        }
                        else {
                            in.seek(HEADER_SIZE + i * 12);
                            var cOffset = in.readLong();
                            var cLen = in.readInt();
                            if (cOffset < 0) {
                                raf.seek(HEADER_SIZE + i * 12L);
                                raf.writeLong(-1);
                                raf.writeInt(0);
                            } else {
                                var buf = new byte[cLen];
                                in.seek(cOffset);
                                in.readFully(buf);
                                this.writeMapChunkInternal(raf, i, offset, buf);
                                offset += cLen;
                            }
                        }
                    }
                    else {
                        raf.seek(HEADER_SIZE + i * 12);
                        raf.writeLong(-1);
                        raf.writeInt(0);
                    }
                    
                }
            } catch (Exception e){
                LOGGER.error("Failed to save region file {}", tempFile, e);
                return;
            }
        }
        try {
            Files.move(tempFile.toPath(), this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            this.dirty = false;
        } catch (Exception e) {
            LOGGER.error("Failed to move temp file {}", file.toFile().getAbsolutePath(), e);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tempFile.delete();
        }
    }
    
    private void writeHeader(RandomAccessFile raf) throws IOException {
        raf.writeInt(MAGIC);
        raf.writeInt(FILE_VERSION);
        raf.writeInt(this.regionPos.x());
        raf.writeInt(this.regionPos.z());
        this.heightMap.write(raf);
    }
    
    private void writeMapChunkInternal(RandomAccessFile raf, int index, long offset, byte[] data) throws IOException {
        raf.seek(HEADER_SIZE + index * 12L);
        raf.writeLong(offset);
        raf.writeInt(data.length);
        raf.seek(offset);
        raf.write(data);
    }
    
    @Override
    public void close() {
//        this.chunkViewCache.close();
        if (this.dirty) CompletableFuture.runAsync(this::save, X3dMapClient.ioExecutor);
    }
}
