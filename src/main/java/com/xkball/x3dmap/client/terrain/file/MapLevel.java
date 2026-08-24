package com.xkball.x3dmap.client.terrain.file;

import com.mojang.blaze3d.GraphicsWorkarounds;
import com.mojang.blaze3d.vertex.UberGpuBuffer;
import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.client.terrain.RegionPos;
import com.xkball.x3dmap.client.terrain.render.GpuNodeModel;
import com.xkball.x3dmap.client.terrain.render.MapNodeModel;
import com.xkball.x3dmap.utils.ExpiringResourceCache;
import com.xkball.x3dmap.utils.MonitoredExecutor;
import com.xkball.xklib.XKLib;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.utils.ClientUtils;
import net.minecraft.client.Minecraft;
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
import java.util.concurrent.Executors;

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
    private final Executor mainThreadExecutor = XKLib.IS_DEBUG ? new MonitoredExecutor(Minecraft.getInstance()) : Minecraft.getInstance();
    private final Executor taskExecutor = XKLib.IS_DEBUG ? new MonitoredExecutor(Executors.newFixedThreadPool(8)) : Executors.newFixedThreadPool(8);
    
    private final ExpiringResourceCache<RegionPos, MapRegion> regionCache = ExpiringResourceCache.<RegionPos, MapRegion>builder()
            .loader((pos) -> {
                var result = new MapRegion(getLevel(), pos, getDir());
                result.load();
                return result;
            })
            .expireAfterRead(300)
            .loadOn(this.taskExecutor)
            .build();
    
    private final ExpiringResourceCache<BlockPos, MapNodeModel> lod0Node = ExpiringResourceCache.<BlockPos, MapNodeModel>builder()
            .asyncLoader(this::createLod0Node)
            .expireAfterRead(20)
            .build();
    
    private final ExpiringResourceCache<BlockPos, MapNodeModel> lod1Node = ExpiringResourceCache.<BlockPos, MapNodeModel>builder()
            .asyncLoader((pos) -> this.createLodNode(pos, 64, lod0Node))
            .expireAfterRead(20)
            .build();
    
    private final ExpiringResourceCache<BlockPos, MapNodeModel> lod2Node = ExpiringResourceCache.<BlockPos, MapNodeModel>builder()
            .asyncLoader((pos) -> this.createLodNode(pos, 128, lod1Node))
            .expireAfterRead(20)
            .build();
    
    private final ExpiringResourceCache<BlockPos, MapNodeModel> lod3Node = ExpiringResourceCache.<BlockPos, MapNodeModel>builder()
            .asyncLoader((pos) -> this.createLodNode(pos, 256, lod2Node))
            .expireAfterRead(20)
            .build();
    
    private final ExpiringResourceCache<BlockPos, MapNodeModel> lod4Node = ExpiringResourceCache.<BlockPos, MapNodeModel>builder()
            .asyncLoader((pos) -> this.createLodNode(pos, 512, lod3Node))
            .expireAfterRead(20)
            .build();
    
    public final UberGpuBuffer<Long> lod0Buffer;
    public final UberGpuBuffer<Long> lod1Buffer;
    public final UberGpuBuffer<Long> lod2Buffer;
    public final UberGpuBuffer<Long> lod3Buffer;
    public final UberGpuBuffer<Long> lod4Buffer;
    private final List<UberGpuBuffer<Long>> lodBuffers;
    
    private final ExpiringResourceCache<BlockPos, GpuNodeModel> lod0GpuNode = ExpiringResourceCache.<BlockPos, GpuNodeModel>builder()
            .asyncLoader(pos -> this.uploadNodeModel(pos, 0,lod0Node))
            .expireAfterRead(10)
            .unloadOn(this.mainThreadExecutor)
            .build();
    
    private final ExpiringResourceCache<BlockPos, GpuNodeModel> lod1GpuNode = ExpiringResourceCache.<BlockPos, GpuNodeModel>builder()
            .asyncLoader(pos -> this.uploadNodeModel(pos, 1, lod1Node))
            .expireAfterRead(10)
            .unloadOn(this.mainThreadExecutor)
            .build();
    
    private final ExpiringResourceCache<BlockPos, GpuNodeModel> lod2GpuNode = ExpiringResourceCache.<BlockPos, GpuNodeModel>builder()
            .asyncLoader(pos -> this.uploadNodeModel(pos, 2, lod2Node))
            .expireAfterRead(10)
            .unloadOn(this.mainThreadExecutor)
            .build();
    
    private final ExpiringResourceCache<BlockPos, GpuNodeModel> lod3GpuNode = ExpiringResourceCache.<BlockPos, GpuNodeModel>builder()
            .asyncLoader(pos -> this.uploadNodeModel(pos, 3, lod3Node))
            .expireAfterRead(10)
            .unloadOn(this.mainThreadExecutor)
            .build();
    
    private final ExpiringResourceCache<BlockPos, GpuNodeModel> lod4GpuNode = ExpiringResourceCache.<BlockPos, GpuNodeModel>builder()
            .asyncLoader(pos -> this.uploadNodeModel(pos, 4, lod4Node))
            .expireAfterRead(10)
            .unloadOn(this.mainThreadExecutor)
            .build();
    
    public MapLevel(Level level, Path dir) {
        this.level = level.dimension().identifier();
        this.dir = dir;
        this.maxY = level.getMaxY();
        this.minY = level.getMinY();
        this.loadRegions();
        var gpuDevice = ClientUtils.getGpuDevice();
        var gpuWorkaround = GraphicsWorkarounds.get(gpuDevice);
        this.lod0Buffer = new UberGpuBuffer<>("x3dmap_terrain_lod0", 64, 32 * 1024 * 1024, 16, gpuDevice, 8 * 1024 * 1024, gpuWorkaround);
        this.lod1Buffer = new UberGpuBuffer<>("x3dmap_terrain_lod1", 64, 32 * 1024 * 1024, 16, gpuDevice, 8 * 1024 * 1024, gpuWorkaround);
        this.lod2Buffer = new UberGpuBuffer<>("x3dmap_terrain_lod2", 64, 32 * 1024 * 1024, 16, gpuDevice, 8 * 1024 * 1024, gpuWorkaround);
        this.lod3Buffer = new UberGpuBuffer<>("x3dmap_terrain_lod3", 64, 32 * 1024 * 1024, 16, gpuDevice, 8 * 1024 * 1024, gpuWorkaround);
        this.lod4Buffer = new UberGpuBuffer<>("x3dmap_terrain_lod4", 64, 32 * 1024 * 1024, 16, gpuDevice, 8 * 1024 * 1024, gpuWorkaround);
        this.lodBuffers = List.of(lod0Buffer, lod1Buffer, lod2Buffer, lod3Buffer, lod4Buffer);
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

    public CompletableFuture<GpuNodeModel> getLod4NodeAsync(BlockPos pos) {
        return this.lod4GpuNode.getAsync(pos);
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
                .thenAccept(region -> region.deleteChunk(pos))
                .thenRun(() -> this.invalidateLODs(pos));
    }

    private @Nullable MapRegion getRegion(ChunkPos pos) {
        return this.regionCache.getOrCreateAsync(RegionPos.ofChunk(pos));
    }
    
    public void updateChunk(MapChunk chunk){
        var pos = chunk.chunkPos;
        this.regions.add(RegionPos.ofChunk(pos));
        this.regionCache.getAsync(RegionPos.ofChunk(pos))
                .thenAccept(region -> region.setChunk(chunk))
                .thenRun(() -> this.invalidateLODs(pos));
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
        return cache.getAsync(pos)
                .thenApplyAsync(model -> {
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
                }, this.mainThreadExecutor);
    }
    
    private void invalidateLODs(ChunkPos pos){
        this.invalidateLOD(pos, 1, this.lod0Node, this.lod0GpuNode);
        this.invalidateLOD(pos, 2, this.lod1Node, this.lod1GpuNode);
        this.invalidateLOD(pos, 3, this.lod2Node, this.lod2GpuNode);
        this.invalidateLOD(pos, 4, this.lod3Node, this.lod3GpuNode);
        this.invalidateLOD(pos, 5, this.lod4Node, this.lod4GpuNode);
    }

    private void invalidateLOD(ChunkPos pos, int shift, ExpiringResourceCache<BlockPos, MapNodeModel> modelCache,
                               ExpiringResourceCache<BlockPos, GpuNodeModel> gpuCache) {
        var nodeBlockBits = shift + 4;
        var x = pos.getMinBlockX() >> nodeBlockBits << nodeBlockBits;
        var z = pos.getMinBlockZ() >> nodeBlockBits << nodeBlockBits;
        var minNodeY = this.minY >> nodeBlockBits;
        var maxNodeY = this.maxY - 1 >> nodeBlockBits;
        for (var y = minNodeY; y <= maxNodeY; y++) {
            var nodePos = new BlockPos(x, y << nodeBlockBits, z);
            modelCache.remove(nodePos);
            gpuCache.remove(nodePos);
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
        this.lod0GpuNode.close();
        this.lod1GpuNode.close();
        this.lod2GpuNode.close();
        this.lod3GpuNode.close();
        this.lod4GpuNode.close();
        this.lod0Node.close();
        this.lod1Node.close();
        this.lod2Node.close();
        this.lod3Node.close();
        this.lod4Node.close();
        this.regionCache.close();
        this.lod0Buffer.close();
        this.lod1Buffer.close();
        this.lod2Buffer.close();
        this.lod3Buffer.close();
        this.lod4Buffer.close();
        closeExecutor(this.taskExecutor);
        closeExecutor(this.mainThreadExecutor);
    }

    private static void closeExecutor(Executor executor) {
        if (!(executor instanceof AutoCloseable closeable)) return;
        try {
            closeable.close();
        } catch (Exception e) {
            LOGGER.error("Failed to close map level executor", e);
        }
    }
}
