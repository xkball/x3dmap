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
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
            .build();
    
    private final ExpiringResourceCache<BlockPos, GpuNodeModel> lod1GpuNode = ExpiringResourceCache.<BlockPos, GpuNodeModel>builder()
            .asyncLoader(pos -> this.uploadNodeModel(pos, 1, lod1Node))
            .expireAfterRead(10)
            .build();
    
    private final ExpiringResourceCache<BlockPos, GpuNodeModel> lod2GpuNode = ExpiringResourceCache.<BlockPos, GpuNodeModel>builder()
            .asyncLoader(pos -> this.uploadNodeModel(pos, 2, lod2Node))
            .expireAfterRead(10)
            .build();
    
    private final ExpiringResourceCache<BlockPos, GpuNodeModel> lod3GpuNode = ExpiringResourceCache.<BlockPos, GpuNodeModel>builder()
            .asyncLoader(pos -> this.uploadNodeModel(pos, 3, lod3Node))
            .expireAfterRead(10)
            .build();
    
    private final ExpiringResourceCache<BlockPos, GpuNodeModel> lod4GpuNode = ExpiringResourceCache.<BlockPos, GpuNodeModel>builder()
            .asyncLoader(pos -> this.uploadNodeModel(pos, 4, lod4Node))
            .expireAfterRead(10)
            .build();
    
    public MapLevel(Level level, Path dir) {
        this.level = level.dimension().identifier();
        this.dir = dir;
        this.maxY = level.getMaxY();
        this.minY = level.getMinY();
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
    
    public void updateChunk(MapChunk chunk){
        var pos = chunk.chunkPos;
        this.regionCache.getAsync(RegionPos.ofChunk(pos))
                .thenAccept(region -> region.setChunk(chunk))
                .thenRun(() -> this.invalidateLODs(pos));
    }
    
    public CompletableFuture<GpuNodeModel> uploadNodeModel(BlockPos pos, int lodLevel, ExpiringResourceCache<BlockPos, MapNodeModel> cache){
        return cache.getAsync(pos)
                .thenApplyAsync(model -> {
                    if (model.data.isEmpty()) return new GpuNodeModel(pos.asLong(), 0, 0);
                    var buffer = this.lodBuffers.get(lodLevel);
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
                        return new GpuNodeModel(key, (int) (allocation.getOffsetFromHeap() / NODE_ENTRY_SIZE), model.data.size());
                    } catch (RuntimeException e) {
                        LOGGER.error("Failed to upload map node model at LOD {} for {}", lodLevel, pos, e);
                        throw e;
                    } finally {
                        MemoryUtil.memFree(uploadBuffer);
                    }
                }, this.mainThreadExecutor);
    }
    
    private void invalidateLODs(ChunkPos pos){
        this.invalidateLOD(pos, 1, this.lod0Node);
        this.invalidateLOD(pos, 2, this.lod1Node);
        this.invalidateLOD(pos, 3, this.lod2Node);
        this.invalidateLOD(pos, 4, this.lod3Node);
        this.invalidateLOD(pos, 5, this.lod4Node);
    }

    private void invalidateLOD(ChunkPos pos, int shift, ExpiringResourceCache<BlockPos, MapNodeModel> cache) {
        var x = pos.x() >> shift;
        var z = pos.z() >> shift;
        var minNodeY = SectionPos.blockToSectionCoord(this.minY) >> shift;
        var maxNodeY = SectionPos.blockToSectionCoord(this.maxY) >> shift;
        for (var y = minNodeY; y <= maxNodeY; y++) {
            cache.remove(new BlockPos(x, y, z));
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
        var px = Math.floorDiv(pos.getX(), sideLength);
        var py = Math.floorDiv(pos.getY(), sideLength);
        var pz = Math.floorDiv(pos.getZ(), sideLength);
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
    public void close() throws Exception {
        this.regionCache.close();
        this.lod0Node.close();
        this.lod1Node.close();
        this.lod2Node.close();
        this.lod3Node.close();
        this.lod4Node.close();
        this.lod0Buffer.close();
        this.lod1Buffer.close();
        this.lod2Buffer.close();
        this.lod3Buffer.close();
        this.lod4Buffer.close();
        this.lod0GpuNode.close();
        this.lod1GpuNode.close();
        this.lod2GpuNode.close();
        this.lod3GpuNode.close();
        this.lod4GpuNode.close();
        if(this.mainThreadExecutor instanceof AutoCloseable ac){
            ac.close();
        }
        if(this.taskExecutor instanceof AutoCloseable ac){
            ac.close();
        }
    }
}
