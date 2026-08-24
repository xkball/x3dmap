package com.xkball.x3dmap.client.terrain.file;

import com.mojang.blaze3d.GraphicsWorkarounds;
import com.mojang.blaze3d.vertex.UberGpuBuffer;
import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.X3dMapClient;
import com.xkball.x3dmap.client.terrain.RegionPos;
import com.xkball.x3dmap.client.terrain.render.GpuNodeModel;
import com.xkball.x3dmap.client.terrain.render.MapNodeModel;
import com.xkball.x3dmap.utils.ExpiringResourceCache;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.utils.ClientUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@NonNullByDefault
public class MapLevel implements AutoCloseable{
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NODE_SIDE_LENGTH_BITS = 5;
    private static final int NODE_ENTRY_SIZE = 16;
    private final Identifier level;
    private final Path dir;
    private final int maxY;
    private final int minY;
    private final Set<RegionPos> regions = ConcurrentHashMap.newKeySet();
    private final Executor mainThreadExecutor;
    private final Executor taskExecutor;
    private final ExpiringResourceCache<RegionPos, MapRegion> regionCache;
    private final List<ExpiringResourceCache<BlockPos, MapNodeModel>> lodNodes;
    
    public final UberGpuBuffer<Long> lod0Buffer;
    public final UberGpuBuffer<Long> lod1Buffer;
    public final UberGpuBuffer<Long> lod2Buffer;
    public final UberGpuBuffer<Long> lod3Buffer;
    public final UberGpuBuffer<Long> lod4Buffer;
    private final List<UberGpuBuffer<Long>> lodBuffers;
    private final List<ExpiringResourceCache<BlockPos, GpuNodeModel>> lodGpuNodes;
    
    public MapLevel(Level level, Path dir, Executor mainThreadExecutor, Executor taskExecutor) {
        this.level = level.dimension().identifier();
        this.dir = dir;
        this.maxY = level.getMaxY();
        this.minY = level.getMinY();
        this.mainThreadExecutor = mainThreadExecutor;
        this.taskExecutor = taskExecutor;
        this.regionCache = ExpiringResourceCache.<RegionPos, MapRegion>builder()
                .loader((pos) -> {
                    var result = new MapRegion(this.getLevel(), pos, this.getDir());
                    result.load();
                    return result;
                })
                .expireAfterRead(300)
                .loadOn(X3dMapClient.ioExecutor)
                .build();
        var lod0Node = ExpiringResourceCache.<BlockPos, MapNodeModel>builder()
                .asyncLoader(this::createLod0Node)
                .expireAfterRead(20)
                .build();
        var lod1Node = ExpiringResourceCache.<BlockPos, MapNodeModel>builder()
                .asyncLoader((pos) -> this.createLodNode(pos, 64, lod0Node))
                .expireAfterRead(20)
                .build();
        var lod2Node = ExpiringResourceCache.<BlockPos, MapNodeModel>builder()
                .asyncLoader((pos) -> this.createLodNode(pos, 128, lod1Node))
                .expireAfterRead(20)
                .build();
        var lod3Node = ExpiringResourceCache.<BlockPos, MapNodeModel>builder()
                .asyncLoader((pos) -> this.createLodNode(pos, 256, lod2Node))
                .expireAfterRead(20)
                .build();
        var lod4Node = ExpiringResourceCache.<BlockPos, MapNodeModel>builder()
                .asyncLoader((pos) -> this.createLodNode(pos, 512, lod3Node))
                .expireAfterRead(20)
                .build();
        this.lodNodes = List.of(lod0Node, lod1Node, lod2Node, lod3Node, lod4Node);
        var gpuDevice = ClientUtils.getGpuDevice();
        var gpuWorkaround = GraphicsWorkarounds.get(gpuDevice);
        this.lod0Buffer = new UberGpuBuffer<>("x3dmap_terrain_lod0", 64, 16 * 1024 * 1024, 16, gpuDevice, 4 * 1024 * 1024, gpuWorkaround);
        this.lod1Buffer = new UberGpuBuffer<>("x3dmap_terrain_lod1", 64, 16 * 1024 * 1024, 16, gpuDevice, 4 * 1024 * 1024, gpuWorkaround);
        this.lod2Buffer = new UberGpuBuffer<>("x3dmap_terrain_lod2", 64, 16 * 1024 * 1024, 16, gpuDevice, 4 * 1024 * 1024, gpuWorkaround);
        this.lod3Buffer = new UberGpuBuffer<>("x3dmap_terrain_lod3", 64, 16 * 1024 * 1024, 16, gpuDevice, 4 * 1024 * 1024, gpuWorkaround);
        this.lod4Buffer = new UberGpuBuffer<>("x3dmap_terrain_lod4", 64, 16 * 1024 * 1024, 16, gpuDevice, 4 * 1024 * 1024, gpuWorkaround);
        this.lodBuffers = List.of(lod0Buffer, lod1Buffer, lod2Buffer, lod3Buffer, lod4Buffer);
        var lod0GpuNode = this.createGpuNodeCache(0,10);
        var lod1GpuNode = this.createGpuNodeCache(1,20);
        var lod2GpuNode = this.createGpuNodeCache(2,20);
        var lod3GpuNode = this.createGpuNodeCache(3,20);
        var lod4GpuNode = this.createGpuNodeCache(4,1000);
        this.lodGpuNodes = List.of(lod0GpuNode, lod1GpuNode, lod2GpuNode, lod3GpuNode, lod4GpuNode);
        this.loadRegions();
    }
    
    public Identifier getLevel() {
        return level;
    }
    
    public Path getDir() {
        return dir;
    }

    public int getMaxY() {
        return this.maxY;
    }

    public int getMinY() {
        return this.minY;
    }

    public List<UberGpuBuffer<Long>> getGpuBuffers() {
        return this.lodBuffers;
    }

    public Set<RegionPos> getRegions() {
        return this.regions;
    }

    public List<MapChunk> getChunks() {
        var result = new ArrayList<MapChunk>();
        for (var region : this.regionCache.values()) {
            result.addAll(region.getChunks());
        }
        return result;
    }

    public int getGpuNodeCacheSize(int lodLevel) {
        return this.lodGpuNodes.get(lodLevel).size();
    }

    public CompletableFuture<GpuNodeModel> getLod4NodeAsync(BlockPos pos) {
        return this.getGpuNodeAsync(pos, 4);
    }

    public boolean containsChunk(ChunkPos pos) {
        var region = this.getRegion(pos);
        return region != null && region.containsChunk(pos);
    }

    public int getHeight(int x, int z) {
        var pos = new ChunkPos(x >> 4, z >> 4);
        var region = this.getRegion(pos);
        if (region == null || !region.containsChunk(pos)) return this.minY;
        return region.getHeight(x, z);
    }

    public int getColor(int x, int z) {
        var pos = new ChunkPos(x >> 4, z >> 4);
        var region = this.getRegion(pos);
        if (region == null || !region.containsChunk(pos)) return 0;
        return region.getColor(x, z);
    }

    public void deleteChunk(ChunkPos pos) {
        this.regionCache.getAsync(RegionPos.ofChunk(pos))
                .thenAcceptAsync(region -> {
                    region.deleteChunk(pos);
                    this.invalidateLODs(pos);
                }, this.mainThreadExecutor);
    }

    private @Nullable MapRegion getRegion(ChunkPos pos) {
        return this.regionCache.getOrCreateAsync(RegionPos.ofChunk(pos));
    }
    
    public void updateChunk(MapChunk chunk){
        var pos = chunk.chunkPos;
        this.regions.add(RegionPos.ofChunk(pos));
        this.regionCache.getAsync(RegionPos.ofChunk(pos))
                .thenAcceptAsync(region -> {
                    region.setChunk(chunk);
                    this.invalidateLODs(pos);
                }, this.taskExecutor);
    }

    private void loadRegions() {
        if (!Files.isDirectory(this.dir)) return;
        try (var paths = Files.list(this.dir)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                var parts = path.getFileName().toString().split(",", -1);
                if (parts.length != 2) return;
                try {
                    var pos = new RegionPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                    this.regions.add(pos);
                    this.regionCache.getAsync(pos);
                } catch (NumberFormatException ignored) {
                }
            });
        } catch (IOException e) {
            LOGGER.error("Failed to load map region index for {}", this.level, e);
        }
    }
    
    public CompletableFuture<GpuNodeModel> uploadNodeModel(BlockPos pos, int lodLevel, ExpiringResourceCache<BlockPos, MapNodeModel> cache){
        return cache.getAsync(pos).thenApplyAsync(model -> this.uploadNodeModel(pos, lodLevel, model), this.mainThreadExecutor);
    }

    private GpuNodeModel uploadNodeModel(BlockPos pos, int lodLevel, MapNodeModel model) {
        var buffer = this.lodBuffers.get(lodLevel);
        if (model.data.isEmpty()) return new GpuNodeModel(buffer, pos.asLong(), null, 0, 0);
        var key = pos.asLong();
        var uploadBuffer = MemoryUtil.memAlloc(model.data.size() * NODE_ENTRY_SIZE);
        try {
            for (var entry : model.data.int2ObjectEntrySet()) {
                var index = entry.getIntKey();
                var x = ((model.x << NODE_SIDE_LENGTH_BITS) + (index >> 10 & 31)) << (model.depth - NODE_SIDE_LENGTH_BITS);
                var y = ((model.y << NODE_SIDE_LENGTH_BITS) + (index >> 5 & 31)) << (model.depth - NODE_SIDE_LENGTH_BITS);
                var z = ((model.z << NODE_SIDE_LENGTH_BITS) + (index & 31)) << (model.depth - NODE_SIDE_LENGTH_BITS);
                uploadBuffer.putLong(BlockPos.asLong(x, y, z));
                uploadBuffer.putInt(entry.getValue().color());
                uploadBuffer.put((byte) entry.getValue().mask());
                uploadBuffer.put((byte) 0);
                uploadBuffer.put((byte) 0);
                uploadBuffer.put((byte) 0);
            }
            uploadBuffer.flip();
            if (!buffer.addAllocation(key, null, uploadBuffer)) {
                buffer.uploadStagedAllocations(ClientUtils.getGpuDevice(), ClientUtils.getCommandEncoder());
                if (!buffer.addAllocation(key, null, uploadBuffer)) {
                    throw new IllegalStateException("Failed to stage map node model upload");
                }
            }
            buffer.uploadStagedAllocations(ClientUtils.getGpuDevice(), ClientUtils.getCommandEncoder());
            var allocation = buffer.getAllocation(key);
            if (allocation == null) {
                throw new IllegalStateException("Map node model allocation is missing after upload");
            }
            return new GpuNodeModel(buffer, key, allocation, (int) (allocation.getOffsetFromHeap() / NODE_ENTRY_SIZE), model.data.size());
        } catch (RuntimeException e) {
            LOGGER.error("Failed to upload map node model at LOD {} for {}", lodLevel, pos, e);
            throw e;
        } finally {
            MemoryUtil.memFree(uploadBuffer);
        }
    }

    private ExpiringResourceCache<BlockPos, GpuNodeModel> createGpuNodeCache(int lodLevel, int expireTime) {
        return ExpiringResourceCache.<BlockPos, GpuNodeModel>builder()
                .asyncLoader(pos -> this.uploadNodeModel(pos, lodLevel, this.lodNodes.get(lodLevel)))
                .expireAfterRead(expireTime)
                .unloadOn(this.mainThreadExecutor)
                .build();
    }

    private CompletableFuture<GpuNodeModel> getGpuNodeAsync(BlockPos pos, int lodLevel) {
        var lodCache = this.lodGpuNodes.get(lodLevel);
        var cached = lodCache.get(pos);
        if (cached == null){
            if(!lodCache.loading(pos)) CompletableFuture.runAsync(() -> lodCache.getAsync(pos), X3dMapClient.taskExecutor);
            return new CompletableFuture<>();
        }
        this.refreshGpuNode(cached, pos, lodLevel);
        var current = lodCache.get(pos);
        return CompletableFuture.completedFuture(current == null ? cached : current);
    }

    private void refreshGpuNode(GpuNodeModel gpuNode, BlockPos pos, int lodLevel) {
        var revision = gpuNode.beginRefresh();
        if (revision < 0) return;
        this.lodNodes.get(lodLevel).getAsync(pos).whenCompleteAsync((model, error) -> {
            if (error != null || this.lodGpuNodes.get(lodLevel).get(pos) != gpuNode || !gpuNode.isRevisionCurrent(revision)) {
                gpuNode.finishRefresh();
                return;
            }
            try {
                this.lodGpuNodes.get(lodLevel).replace(pos, this.uploadNodeModel(pos, lodLevel, model));
            } catch (RuntimeException e) {
                gpuNode.finishRefresh();
                throw e;
            }
        }, this.mainThreadExecutor);
    }
    
    private void invalidateLODs(ChunkPos pos){
        for (var lodLevel = 0; lodLevel < this.lodNodes.size(); lodLevel++) {
            this.invalidateLOD(pos, lodLevel, lodLevel + 1);
        }
    }

    private void invalidateLOD(ChunkPos pos, int lodLevel, int shift) {
        var nodeBlockBits = shift + 4;
        var x = pos.getMinBlockX() >> nodeBlockBits << nodeBlockBits;
        var z = pos.getMinBlockZ() >> nodeBlockBits << nodeBlockBits;
        var minNodeY = this.minY >> nodeBlockBits;
        var maxNodeY = this.maxY - 1 >> nodeBlockBits;
        for (var y = minNodeY; y <= maxNodeY; y++) {
            var nodePos = new BlockPos(x, y << nodeBlockBits, z);
            this.lodNodes.get(lodLevel).remove(nodePos);
            this.lodGpuNodes.get(lodLevel).cancelLoad(nodePos);
            var gpuNode = this.lodGpuNodes.get(lodLevel).get(nodePos);
            if (gpuNode != null) gpuNode.invalidate();
        }
    }
    
    @SuppressWarnings("SequencedCollectionMethodCanBeUsed")
    private CompletableFuture<MapNodeModel> createLod0Node(BlockPos pos) {
        var chunkPos = ChunkPos.containing(pos);
        var regionPos = RegionPos.ofChunk(chunkPos);
        return this.regionCache.getAsync(regionPos)
                .thenCompose((r) -> r.getMapChunkViews(List.of(
                        chunkPos,
                        new ChunkPos(chunkPos.x()+1, chunkPos.z()),
                        new ChunkPos(chunkPos.x(), chunkPos.z()+1),
                        new ChunkPos(chunkPos.x()+1, chunkPos.z()+1))))
                .thenApplyAsync((list) -> new MapNodeModel(chunkPos, SectionPos.blockToSectionCoord(pos.getY()),list.get(0),list.get(1), list.get(2), list.get(3)), this.taskExecutor);
    }
    
    private CompletableFuture<MapNodeModel> createLodNode(BlockPos pos, int sideLength, ExpiringResourceCache<BlockPos, MapNodeModel> subNodeSource){
        var px = Math.floorDiv(pos.getX(), sideLength) * sideLength;
        var py = Math.floorDiv(pos.getY(), sideLength) * sideLength;
        var pz = Math.floorDiv(pos.getZ(), sideLength) * sideLength;
        var subSideLength = sideLength / 2;
        return subNodeSource.getListAsync(List.of(
                        new BlockPos(px,py,pz),
                        new BlockPos(px + subSideLength,py,pz),
                        new BlockPos(px,py,pz + subSideLength),
                        new BlockPos(px + subSideLength,py,pz + subSideLength),
                        new BlockPos(px,py + subSideLength,pz),
                        new BlockPos(px + subSideLength,py + subSideLength,pz),
                        new BlockPos(px,py + subSideLength,pz + subSideLength),
                        new BlockPos(px + subSideLength,py + subSideLength,pz + subSideLength)
                ))
                .thenApplyAsync(MapNodeModel::new, this.taskExecutor);
    }
    
    @Override
    public void close() {
        for (var cache : this.lodGpuNodes) {
            cache.close();
        }
        for (var cache : this.lodNodes) {
            cache.close();
        }
        this.regionCache.close();
        this.lod0Buffer.close();
        this.lod1Buffer.close();
        this.lod2Buffer.close();
        this.lod3Buffer.close();
        this.lod4Buffer.close();
    }

}
