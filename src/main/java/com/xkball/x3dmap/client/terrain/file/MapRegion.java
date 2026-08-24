package com.xkball.x3dmap.client.terrain.file;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.client.terrain.RegionPos;
import com.xkball.x3dmap.client.terrain.render.MapNodeModel;
import com.xkball.x3dmap.utils.ExpiringResourceCache;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@NonNullByDefault
public class MapRegion implements AutoCloseable{
    
    private static final Logger LOGGER = LogUtils.getLogger();
    public final RegionPos regionPos;
    private final Identifier level;
    private final Path dir;
    private final MapRegionHeightMap heightMap;
    private final ExpiringResourceCache<RegionChunks> chunks;
    //总是从区块生成, 不持久化
    private final ExpiringResourceCache<RegionLOD> lod0;
    //下面的要持久化
    private final ExpiringResourceCache<RegionLOD> lod1;
    private final ExpiringResourceCache<RegionLOD> lod2;
    private final ExpiringResourceCache<RegionLOD> lod3;
    private final ExpiringResourceCache<RegionLOD> lod4;
    
    public MapRegion(Identifier level, RegionPos regionPos, Path dir) {
        this.regionPos = regionPos;
        this.level = level;
        this.dir = dir.resolve(this.regionPos.x() + "," + this.regionPos.z());
        this.heightMap = new MapRegionHeightMap();
        this.heightMap.load(this.dir);
        this.chunks = ExpiringResourceCache.<RegionChunks>builder()
                .loader(() -> new RegionChunks(this.regionPos, this.level, this.dir))
                .unloader((r) -> r.save(this.dir))
                .expireAfterRead(300)
                .build();
        this.lod0 = ExpiringResourceCache.<RegionLOD>builder()
                .loader(() -> new RegionLOD(5))
                .expireAfterRead(300)
                .build();
        Function<Integer, RegionLOD> loader = (i) -> {
            var result = new RegionLOD(i);
            result.load(this.dir);
            return result;
        };
        this.lod1 = ExpiringResourceCache.<RegionLOD>builder()
                .loader(() -> loader.apply(6))
                .unloader((r) -> r.save(this.dir))
                .expireAfterRead(300)
                .build();
        this.lod2 = ExpiringResourceCache.<RegionLOD>builder()
                .loader(() -> loader.apply(7))
                .unloader((r) -> r.save(this.dir))
                .expireAfterRead(300)
                .build();
        this.lod3 = ExpiringResourceCache.<RegionLOD>builder()
                .loader(() -> loader.apply(8))
                .unloader((r) -> r.save(this.dir))
                .expireAfterRead(300)
                .build();
        this.lod4 = ExpiringResourceCache.<RegionLOD>builder()
                .loader(() -> loader.apply(9))
                .unloader((r) -> r.save(this.dir))
                .expireAfterRead(300)
                .build();
    }
    
    public CompletableFuture<List<MapChunk>> getChunks() {
        return this.chunks.getAsync().thenApply(cs -> {
            List<MapChunk> list = new ArrayList<>();
            for (MapChunk chunk : cs.chunks) {
                if (chunk != null) {
                    list.add(chunk);
                }
            }
            return list;
        });
    }
    
    public void setChunk(MapChunk chunk) {
        this.chunks.getAsync().thenAccept(r -> r.setChunk(chunk));
        this.heightMap.setChunk(chunk);
    }
    
    public void deleteChunk(ChunkPos chunkPos) {
        this.chunks.getAsync().thenAccept(r -> r.deleteChunk(chunkPos));
        this.heightMap.deleteChunk(chunkPos);
    }

    public boolean containsChunk(ChunkPos chunkPos) {
        return this.heightMap.chunkExists(chunkPos.getRegionLocalX(), chunkPos.getRegionLocalZ());
    }
    
    public int getHeight(int x, int z) {
        return this.heightMap.getHeight(x, z);
    }

    public int getColor(int x, int z) {
        return this.heightMap.getColor(x, z);
    }
    
    public void saveAll(){
    
    }
    
    @Override
    public void close() {
        this.saveAll();
    }
    
    public static class RegionChunks implements IMapFile{
        
        private static final long HEADER_SIZE = 16;
        
        private final RegionPos regionPos;
        private final Identifier level;
        private volatile boolean dirty;
        private final @Nullable MapChunk[] chunks = new MapChunk[32 * 32];
        
        public RegionChunks(RegionPos regionPos, Identifier level, Path dir) {
            this.regionPos = regionPos;
            this.level = level;
            this.load(dir);
        }
        
        public void setChunk(MapChunk chunk) {
            var idx = this.getChunkIndex(chunk.chunkPos);
            chunk.state = MapChunk.MapChunkState.DIRTY;
            this.chunks[idx] = chunk;
            this.dirty = true;
        }
        
        public void deleteChunk(ChunkPos chunkPos) {
            this.chunks[this.getChunkIndex(chunkPos)] = null;
            this.dirty = true;
        }
        
        public int getChunkIndex(ChunkPos chunkPos){
            var chunkPos0 = this.regionPos.toChunkPos();
            var dx = chunkPos.x() - chunkPos0.x();
            var dz = chunkPos.z() - chunkPos0.z();
            if(dx < 0 || dz < 0 ||dx >= 32 || dz >= 32) throw new IllegalArgumentException("Chunk pos not belongs region " + this.regionPos);
            return dx * 32 + dz;
        }
        
        @Override
        public Path getFile(Path dir) {
            return dir.resolve("0");
        }
        
        @Override
        public boolean dirty() {
            return this.dirty;
        }
        
        @Override
        public void read(RandomAccessFile raf) throws IOException {
            LOGGER.info("Loading map at {}, region {}", this.level, this.regionPos);
            raf.seek(HEADER_SIZE);
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
        
        @Override
        public void write(RandomAccessFile raf, @Nullable RandomAccessFile oldFile) throws IOException {
            LOGGER.info("Saving map at {}, region {}", this.level, this.regionPos);
            if(oldFile == null){
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
            }
            else {
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
                            oldFile.seek(HEADER_SIZE + i * 12);
                            var cOffset = oldFile.readLong();
                            var cLen = oldFile.readInt();
                            if (cOffset < 0) {
                                raf.seek(HEADER_SIZE + i * 12L);
                                raf.writeLong(-1);
                                raf.writeInt(0);
                            } else {
                                var buf = new byte[cLen];
                                oldFile.seek(cOffset);
                                oldFile.readFully(buf);
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
            }
        }
        
        @Override
        public void afterWrite() {
            this.dirty = false;
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
        
        private void writeHeader(RandomAccessFile raf) throws IOException {
            raf.writeInt(MAGIC);
            raf.writeInt(FILE_VERSION);
            raf.writeInt(this.regionPos.x());
            raf.writeInt(this.regionPos.z());
//            this.heightMap.write(raf);
        }
        
        private void writeMapChunkInternal(RandomAccessFile raf, int index, long offset, byte[] data) throws IOException {
            raf.seek(HEADER_SIZE + index * 12L);
            raf.writeLong(offset);
            raf.writeInt(data.length);
            raf.seek(offset);
            raf.write(data);
        }
    }
    
    public static class RegionLOD implements IMapFile{
        
        private final int depth;
        
        public RegionLOD(int depth) {
            this.depth = depth;
        }
        
        public @Nullable MapNodeModel getNodeModel(BlockPos pos) {
        
        }
        
        @Override
        public Path getFile(Path dir) {
            return dir.resolve(Integer.toString(this.depth - 5));
        }
        
        @Override
        public boolean dirty() {
            return false;
        }
        
        @Override
        public void read(RandomAccessFile raf) throws IOException {
        
        }
        
        @Override
        public void write(RandomAccessFile raf, @Nullable RandomAccessFile oldFile) throws IOException {
        
        }
    }
    
    
}
