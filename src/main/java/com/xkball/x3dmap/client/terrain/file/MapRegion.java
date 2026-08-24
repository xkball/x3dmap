package com.xkball.x3dmap.client.terrain.file;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.X3dMapClient;
import com.xkball.x3dmap.client.terrain.RegionPos;
import com.xkball.x3dmap.client.terrain.render.MapNodeModel;
import com.xkball.x3dmap.client.terrain.render.TerrainBlockData;
import com.xkball.x3dmap.utils.ExpiringResourceCache;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        if(this.dir.toFile().isFile()){
            this.dir.toFile().delete();
        }
        this.heightMap = new MapRegionHeightMap();
        this.heightMap.load(this.dir);
        this.chunks = ExpiringResourceCache.<RegionChunks>builder()
                .loader(() -> new RegionChunks(this.regionPos, this.level, this.dir))
                .loadOn(X3dMapClient.ioExecutor)
                .unloader((r) -> r.save(this.dir))
                .unloadOn(X3dMapClient.ioExecutor)
                .expireAfterRead(300)
                .build();
        this.lod0 = ExpiringResourceCache.<RegionLOD>builder()
                .asyncLoader(() -> this.chunks.getAsync().thenApply(RegionLOD::fromChunks))
                .expireAfterRead(300)
                .build();
        Function<Integer, RegionLOD> loader = (i) -> {
            var result = new RegionLOD(i);
            result.load(this.dir);
            return result;
        };
        this.lod1 = ExpiringResourceCache.<RegionLOD>builder()
                .loader(() -> loader.apply(6))
                .loadOn(X3dMapClient.ioExecutor)
                .unloader((r) -> r.save(this.dir))
                .unloadOn(X3dMapClient.ioExecutor)
                .expireAfterRead(300)
                .build();
        this.lod2 = ExpiringResourceCache.<RegionLOD>builder()
                .loader(() -> loader.apply(7))
                .loadOn(X3dMapClient.ioExecutor)
                .unloader((r) -> r.save(this.dir))
                .unloadOn(X3dMapClient.ioExecutor)
                .expireAfterRead(300)
                .build();
        this.lod3 = ExpiringResourceCache.<RegionLOD>builder()
                .loader(() -> loader.apply(8))
                .loadOn(X3dMapClient.ioExecutor)
                .unloader((r) -> r.save(this.dir))
                .unloadOn(X3dMapClient.ioExecutor)
                .expireAfterRead(300)
                .build();
        this.lod4 = ExpiringResourceCache.<RegionLOD>builder()
                .loader(() -> loader.apply(9))
                .loadOn(X3dMapClient.ioExecutor)
                .unloader((r) -> r.save(this.dir))
                .unloadOn(X3dMapClient.ioExecutor)
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
    
    public CompletableFuture<Void> setChunk(MapChunk chunk) {
        return this.chunks.getAsync().thenComposeAsync(chunks -> {
            chunks.setChunk(chunk);
            this.heightMap.setChunk(chunk);
            return this.updateLODs(chunks, chunk.chunkPos);
        }, X3dMapClient.taskExecutor);
    }
    
    public CompletableFuture<Void> deleteChunk(ChunkPos chunkPos) {
        return this.chunks.getAsync().thenComposeAsync(chunks -> {
            chunks.deleteChunk(chunkPos);
            this.heightMap.deleteChunk(chunkPos);
            return this.updateLODs(chunks, chunkPos);
        }, X3dMapClient.taskExecutor);
    }

    public CompletableFuture<List<MapChunk>> getMapChunkViews(List<ChunkPos> chunkPositions) {
        return this.chunks.getAsync().thenApply(chunks -> {
            var result = new ArrayList<MapChunk>(chunkPositions.size());
            for (var chunkPos : chunkPositions) {
                result.add(chunks.getChunkOrEmpty(chunkPos));
            }
            return result;
        });
    }

    public CompletableFuture<@Nullable MapNodeModel> getNodeModel(BlockPos pos, int lodLevel) {
        return this.getLodCache(lodLevel).getAsync().thenApply(l -> l.getNodeModel(pos));
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
        X3dMapClient.ioExecutor.execute(() -> {
            this.heightMap.save(this.dir);
            this.saveCache(this.chunks);
            this.saveCache(this.lod1);
            this.saveCache(this.lod2);
            this.saveCache(this.lod3);
            this.saveCache(this.lod4);
        });
    }
    
    @Override
    public void close() {
        this.heightMap.save(this.dir);
        this.chunks.close();
        this.lod1.close();
        this.lod2.close();
        this.lod3.close();
        this.lod4.close();
    }

    private CompletableFuture<Void> updateLODs(RegionChunks chunks, ChunkPos chunkPos) {
        return this.lod0.getAsync().thenComposeAsync(lod0 -> {
            var lod0Changed = lod0.updateChunk(chunks, chunkPos);
            return this.lod1.getAsync().thenComposeAsync(lod1 -> {
                var lod1Changed = lod1.updateParents(lod0, lod0Changed);
                return this.lod2.getAsync().thenComposeAsync(lod2 -> {
                    var lod2Changed = lod2.updateParents(lod1, lod1Changed);
                    return this.lod3.getAsync().thenComposeAsync(lod3 -> {
                        var lod3Changed = lod3.updateParents(lod2, lod2Changed);
                        return this.lod4.getAsync().thenAcceptAsync(lod4 -> {
                            lod4.updateParents(lod3, lod3Changed);
                        }, X3dMapClient.taskExecutor);
                    }, X3dMapClient.taskExecutor);
                }, X3dMapClient.taskExecutor);
            }, X3dMapClient.taskExecutor);
        }, X3dMapClient.taskExecutor);
    }

    private ExpiringResourceCache<RegionLOD> getLodCache(int lodLevel) {
        return switch (lodLevel) {
            case 0 -> this.lod0;
            case 1 -> this.lod1;
            case 2 -> this.lod2;
            case 3 -> this.lod3;
            case 4 -> this.lod4;
            default -> throw new IllegalArgumentException("Invalid lod level: " + lodLevel);
        };
    }

    private void saveCache(ExpiringResourceCache<? extends IMapFile> cache) {
        var resource = cache.get();
        if (resource != null) resource.save(this.dir);
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

        public @Nullable MapChunk getChunk(ChunkPos chunkPos) {
            return this.chunks[this.getChunkIndex(chunkPos)];
        }

        public MapChunk getChunkOrEmpty(ChunkPos chunkPos) {
            var chunk = this.getChunk(chunkPos);
            return chunk == null ? new MapChunk(chunkPos) : chunk;
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
        
        private static final int NODE_HEADER_SIZE = 8;
        private final int depth;
        private final Map<BlockPos, MapNodeModel> nodes = new HashMap<>();
        private volatile boolean dirty;
        
        public RegionLOD(int depth) {
            this.depth = depth;
        }

        private static RegionLOD fromChunks(RegionChunks chunks) {
            var result = new RegionLOD(5);
            var positions = new HashSet<BlockPos>();
            for (var chunk : chunks.chunks) {
                if(chunk == null) continue;
                chunk.data.forEach((entry, _) -> positions.add(result.normalize(entry.x(), entry.y(), entry.z())));
            }
            for (var pos : positions) {
                result.updateNode(chunks, pos);
            }
            return result;
        }
        
        public @Nullable MapNodeModel getNodeModel(BlockPos pos) {
            return this.nodes.get(this.normalize(pos));
        }

        public Collection<MapNodeModel> getNodeModels() {
            return this.nodes.values();
        }

        private synchronized Set<BlockPos> updateChunk(RegionChunks chunks, ChunkPos chunkPos) {
            var positions = new HashSet<BlockPos>();
            var nodeX = Math.floorDiv(chunkPos.getMinBlockX(), this.nodeSideLength()) * this.nodeSideLength();
            var nodeZ = Math.floorDiv(chunkPos.getMinBlockZ(), this.nodeSideLength()) * this.nodeSideLength();
            for (var pos : this.nodes.keySet()) {
                if (pos.getX() == nodeX && pos.getZ() == nodeZ) positions.add(pos);
            }
            var chunk = chunks.getChunk(chunkPos);
            if (chunk != null) {
                chunk.data.forEach((entry, _) -> positions.add(this.normalize(entry.x(), entry.y(), entry.z())));
            }
            for (var pos : positions) {
                this.updateNode(chunks, pos);
            }
            return positions;
        }

        private void updateNode(RegionChunks chunks, BlockPos pos) {
            var model = this.createNode(chunks, pos);
            if (model == null || model.isEmpty()) {
                if (this.nodes.remove(pos) != null) this.dirty = true;
                return;
            }
            this.nodes.put(pos, model);
            this.dirty = true;
        }

        private @Nullable MapNodeModel createNode(RegionChunks chunks, BlockPos pos) {
            if (this.depth != 5) return null;
            var chunkPos = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
            var sectionY = Math.floorDiv(pos.getY(), 16);
            return new MapNodeModel(
                    chunkPos,
                    sectionY,
                    chunks.getChunkOrEmpty(chunkPos),
                    chunks.getChunkOrEmpty(new ChunkPos(chunkPos.x() + 1, chunkPos.z())),
                    chunks.getChunkOrEmpty(new ChunkPos(chunkPos.x(), chunkPos.z() + 1)),
                    chunks.getChunkOrEmpty(new ChunkPos(chunkPos.x() + 1, chunkPos.z() + 1)));
        }

        private synchronized Set<BlockPos> updateParents(RegionLOD lower, Set<BlockPos> changedChildren) {
            var changedParents = new HashSet<BlockPos>();
            for (var childPos : changedChildren) {
                var parentPos = this.normalize(childPos);
                var subNodes = new ArrayList<MapNodeModel>(8);
                var subSideLength = this.nodeSideLength() / 2;
                var hasData = false;
                for (var yOffset = 0; yOffset < 2; yOffset++) {
                    for (var zOffset = 0; zOffset < 2; zOffset++) {
                        for (var xOffset = 0; xOffset < 2; xOffset++) {
                            var child = new BlockPos(
                                    parentPos.getX() + xOffset * subSideLength,
                                    parentPos.getY() + yOffset * subSideLength,
                                    parentPos.getZ() + zOffset * subSideLength);
                            var model = lower.getNodeModel(child);
                            if (model == null) model = this.emptyNode(lower.depth, child);
                            else if (!model.isEmpty()) hasData = true;
                            subNodes.add(model);
                        }
                    }
                }
                if (!hasData) {
                    if (this.nodes.remove(parentPos) != null) this.dirty = true;
                } else {
                    this.nodes.put(parentPos, new MapNodeModel(subNodes));
                    this.dirty = true;
                }
                changedParents.add(parentPos);
            }
            return changedParents;
        }

        private MapNodeModel emptyNode(int nodeDepth, BlockPos pos) {
            var sideLength = 1 << nodeDepth;
            return new MapNodeModel(
                    nodeDepth,
                    Math.floorDiv(pos.getX(), sideLength),
                    Math.floorDiv(pos.getY(), sideLength),
                    Math.floorDiv(pos.getZ(), sideLength),
                    new Int2ObjectOpenHashMap<>());
        }

        private BlockPos normalize(BlockPos pos) {
            return this.normalize(pos.getX(), pos.getY(), pos.getZ());
        }

        private BlockPos normalize(int x, int y, int z) {
            var sideLength = this.nodeSideLength();
            return new BlockPos(
                    Math.floorDiv(x, sideLength) * sideLength,
                    Math.floorDiv(y, sideLength) * sideLength,
                    Math.floorDiv(z, sideLength) * sideLength);
        }

        private int nodeSideLength() {
            return 1 << this.depth;
        }
        
        @Override
        public Path getFile(Path dir) {
            return dir.resolve(Integer.toString(this.depth - 5));
        }
        
        @Override
        public boolean dirty() {
            return this.dirty;
        }
        
        @Override
        public void read(RandomAccessFile raf) throws IOException {
            raf.seek(NODE_HEADER_SIZE);
            var fileDepth = raf.readInt();
            var size = raf.readInt();
            if (fileDepth != this.depth) {
                throw new IOException("Map LOD depth does not match file");
            }
            var loaded = new HashMap<BlockPos, MapNodeModel>(size);
            for (int i = 0; i < size; i++) {
                var key = BlockPos.of(raf.readLong());
                var length = raf.readInt();
                var compressed = new byte[length];
                raf.readFully(compressed);
                var decoded = VanillaUtils.unGzip(compressed);
                var input = Unpooled.wrappedBuffer(decoded);
                var model = readNode(input);
                if (model.depth != this.depth
                        || !this.normalize(key).equals(key)
                        || model.x != Math.floorDiv(key.getX(), this.nodeSideLength())
                        || model.y != Math.floorDiv(key.getY(), this.nodeSideLength())
                        || model.z != Math.floorDiv(key.getZ(), this.nodeSideLength())) {
                    throw new IOException("Map LOD node has invalid coordinates");
                }
                loaded.put(key, model);
            }
            synchronized (this) {
                this.nodes.clear();
                this.nodes.putAll(loaded);
                this.dirty = false;
            }
        }
        
        @Override
        public void write(RandomAccessFile raf, @Nullable RandomAccessFile oldFile) throws IOException {
            List<Map.Entry<BlockPos, MapNodeModel>> entries;
            synchronized (this) {
                entries = new ArrayList<>(this.nodes.entrySet());
            }
            entries.sort(Comparator.comparingLong(entry -> entry.getKey().asLong()));
            raf.setLength(0);
            raf.writeInt(MAGIC);
            raf.writeInt(FILE_VERSION);
            raf.writeInt(this.depth);
            raf.writeInt(entries.size());
            for (var entry : entries) {
                var output = Unpooled.buffer();
                writeNode(output, entry.getValue());
                var data = VanillaUtils.gzip(output.array(), 0, output.readableBytes());
                raf.writeLong(entry.getKey().asLong());
                raf.writeInt(data.length);
                raf.write(data);
            }
        }

        @Override
        public void afterRead() {
            this.dirty = false;
        }

        @Override
        public void afterWrite() {
            this.dirty = false;
        }

        private static void writeNode(ByteBuf output, MapNodeModel model) {
            output.writeInt(model.depth);
            output.writeInt(model.x);
            output.writeInt(model.y);
            output.writeInt(model.z);
            output.writeInt(model.data.size());
            for (var entry : model.data.int2ObjectEntrySet()) {
                output.writeInt(entry.getIntKey());
                TerrainBlockData.STREAM_CODEC.encode(output, entry.getValue());
            }
        }

        private static MapNodeModel readNode(ByteBuf input) throws IOException {
            if (input.readableBytes() < 20) {
                throw new IOException("Map LOD node data is truncated");
            }
            var depth = input.readInt();
            var x = input.readInt();
            var y = input.readInt();
            var z = input.readInt();
            var size = input.readInt();
            if (size < 0) {
                throw new IOException("Map LOD node data size is negative");
            }
            var data = new Int2ObjectOpenHashMap<TerrainBlockData>(size);
            for (int i = 0; i < size; i++) {
                if (input.readableBytes() < 5) {
                    throw new IOException("Map LOD node data is truncated");
                }
                data.put(input.readInt(), TerrainBlockData.STREAM_CODEC.decode(input));
            }
            return new MapNodeModel(depth, x, y, z, data);
        }
    }
    
    
}
