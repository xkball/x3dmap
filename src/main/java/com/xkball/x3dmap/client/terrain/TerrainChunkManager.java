package com.xkball.x3dmap.client.terrain;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.ClientConfig;
import com.xkball.x3dmap.X3dMapClient;
import com.xkball.x3dmap.client.map.plugin.X3dMapPluginRegistry;
import com.xkball.x3dmap.api.mixin.IExtendedTlsfAllocation;
import com.xkball.x3dmap.client.map.compatibility.CompatibilityExtension;
import com.xkball.x3dmap.client.render.pip.layers.TerrainRenderer;
import com.xkball.x3dmap.utils.DualQueueThreadPool;
import com.xkball.x3dmap.utils.X3dClientUtils;
import com.xkball.xklibmc.XKLibMCClient;
import com.xkball.xklibmc.api.client.b3d.ICloseOnExit;
import com.xkball.xklibmc.client.b3d.IndirectDrawCommand;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.utils.ClientUtils;
import com.xkball.xklibmc.utils.VanillaUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(Dist.CLIENT)
@NonNullByDefault
public class TerrainChunkManager implements ICloseOnExit<TerrainChunkManager> {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final TerrainChunkManager INSTANCE = new TerrainChunkManager();
    
    public final Map<ResourceKey<Level>, LevelChunkStorage> storageMap = new ConcurrentHashMap<>();
    public final DualQueueThreadPool taskQueue = new DualQueueThreadPool();
    public final X3dMapPluginRegistry mapPluginRegistry = new X3dMapPluginRegistry();
    public boolean compatibleMode = false;
    public List<String> compatibilityReasons = Collections.emptyList();
    public boolean compatibilityWarningSuppressed = false;
    public int viewDistance = 1024;
    private final ArrayDeque<ChunkPos> updateQueue = new ArrayDeque<>();
    private final Set<ChunkPos> updateChunkSet = new HashSet<>();
    
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        INSTANCE.initializeMapApi();
        if (!Minecraft.getInstance().isPaused() && Minecraft.getInstance().level != null) {
            INSTANCE.tryLoadLevel(Minecraft.getInstance().level);
            INSTANCE.taskQueue.runFor10ms();
            for (var s : INSTANCE.storageMap.values()) {
                for (var b : s.getGpuBuffers()) {
                    if (!b.stagedAllocations.isEmpty()) {
                        b.uploadStagedAllocations(ClientUtils.getGpuDevice(), ClientUtils.getCommandEncoder());
                    }
                }
            }
        }
        if (XKLibMCClient.tickCount % 100 == 0) {
            var level = Minecraft.getInstance().level;
            if (level != null) {
                var storage = INSTANCE.storageMap.get(level.dimension());
                if (storage != null) {
                    INSTANCE.checkRegionResidency(storage);
                }
            }
        }
        int drawInterval = ClientConfig.DRAW_NEW_CHUNK_INTERVAL.get();
        if (drawInterval > 0 && XKLibMCClient.tickCount % drawInterval == 0) {
            INSTANCE.processUpdateQueue(ClientConfig.DRAW_NEW_CHUNK_COUNT.get());
        }
        int saveInterval = ClientConfig.AUTO_SAVE_INTERVAL.get();
        if (saveInterval > 0 && XKLibMCClient.tickCount % saveInterval == 0) {
            for (var s : INSTANCE.storageMap.values()) {
                s.saveFile(true);
            }
            INSTANCE.mapPluginRegistry.saveData();
        }
    }
    
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        var level = event.getLevel();
        if (!level.isClientSide()) return;
        var chunkPos = event.getChunk().getPos();
        if (X3dClientUtils.isClientChunkAroundLoaded(chunkPos)){
            INSTANCE.enqueueUpdate(event.getChunk().getPos());
        }
        int x = chunkPos.x();
        int z = chunkPos.z();
        var storage = INSTANCE.getCurrentLevelChunkStorage();
        if(storage == null) return;
        if (X3dClientUtils.isClientChunkAroundLoaded(chunkPos)){
            INSTANCE.enqueueUpdate(new ChunkPos(x-1,z));
        }
        if (X3dClientUtils.isClientChunkAroundLoaded(chunkPos)){
            INSTANCE.enqueueUpdate(new ChunkPos(x+1,z));
        }
        if (X3dClientUtils.isClientChunkAroundLoaded(chunkPos)){
            INSTANCE.enqueueUpdate(new ChunkPos(x,z-1));
        }
        if (X3dClientUtils.isClientChunkAroundLoaded(chunkPos)){
            INSTANCE.enqueueUpdate(new ChunkPos(x,z+1));
        }
    }
    
    @SubscribeEvent
    public static void onClientLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        INSTANCE.setCloseOnExit();
        INSTANCE.initializeMapApi();
        INSTANCE.mapPluginRegistry.openRuntime(ClientUtils.getSaveOrServerName());
        CompatibilityExtension.initCompatibilityMode();
        if (!INSTANCE.storageMap.containsKey(level.dimension())) {
            INSTANCE.tryLoadLevel(level);
        } else {
            var s = INSTANCE.storageMap.get(level.dimension());
            INSTANCE.mapPluginRegistry.openLevel(s.dimension, s.getDirectory());
            s.loadFile();
        }
    }
    
    @SubscribeEvent
    public static void onClientLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        var level = Minecraft.getInstance().level;
        INSTANCE.unloadLevel(level);
        INSTANCE.mapPluginRegistry.closeRuntime();
    }
    
    public void enqueueUpdate(ChunkPos chunkPos) {
        if (!updateChunkSet.contains(chunkPos)) {
            updateQueue.add(chunkPos);
            updateChunkSet.add(chunkPos);
        }
    }
    
    private void processUpdateQueue(int count) {
        if(X3dMapClient.loading) return;
        for (int i = 0; i < count && !updateQueue.isEmpty(); i++) {
            var chunkPos = updateQueue.pollFirst();
            updateChunkSet.remove(chunkPos);
            this.submitUpdate(chunkPos, true);
        }
    }
    
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if(event.getLevel() instanceof ClientLevel level) INSTANCE.unloadLevel(level);
    }
    
    public TerrainChunkManager() {
    }

    public void initializeMapApi() {
        this.mapPluginRegistry.initialize(this);
    }
    
    public void tryLoadLevel(Level level) {
        this.initializeMapApi();
        this.mapPluginRegistry.openRuntime(ClientUtils.getSaveOrServerName());
        if (!this.storageMap.containsKey(level.dimension())) {
            var s = new LevelChunkStorage(level.dimension(), level.getMinY(), level.getMaxY(), this.compatibleMode);
            this.mapPluginRegistry.openLevel(s.dimension, s.getDirectory());
            s.loadFile();
            this.storageMap.put(level.dimension(), s);
        }
    }
    
    public @Nullable LevelChunkStorage getCurrentLevelChunkStorage() {
        if (Minecraft.getInstance().level == null) return null;
        return this.storageMap.get(Minecraft.getInstance().level.dimension());
    }
    
    public int getLodLevel(Vector3f pos, int baseLodDistance, Vector3f camPos) {
        var len = camPos.distance(pos);
        return getLodLevel(len, baseLodDistance);
    }
    
    public int getLodLevel(float len, int baseLodDistance) {
        if (len < baseLodDistance) {
            return 0;
        } else if (len < baseLodDistance + 1000) {
            return 1;
        } else if (len < baseLodDistance + 2000) {
            return 2;
        } else if (len < baseLodDistance + 4000) {
            return 3;
        }
        return 4;
    }
    
    public RenderInfo gatherRenderInfo(Frustum frustum, boolean cullNear, Vector3f camPos, Vector3f camTar, int baseLodDistance) {
        var level = Minecraft.getInstance().level;
        if (level == null) return RenderInfo.empty();
        var storage = this.storageMap.get(level.dimension());
        if (storage == null) return RenderInfo.empty();
        assert storage.gpuBufferBlockData != null;
        var gather = new RenderInfoBlockGather();
        var gather2 = new RenderInfoWithBufferBlockGather();
        for (var region : storage.regionMap.values()) {
            if (!frustum.isVisible(region.aabb)) continue;
            var lod = this.getLodLevel(region.aabb.getCenter().toVector3f(), (int) (baseLodDistance + 256 * Math.sqrt(2)), camPos);
            if (lod == 0) {
                var haveRegion = storage.residentRegions.contains(region.regionPos);
                for (var chunk : region.chunks()) {
                    var chunkLod = this.getLodLevel(chunk.aabb.getCenter().toVector3f(), baseLodDistance, camPos);
                    var aabb = chunk.aabb;
                    if (cullNear && new Vector2f((float) Mth.lerp(0.5f, aabb.minX, aabb.maxX), (float) Mth.lerp(0.5f, aabb.minZ, aabb.maxZ)).sub(new Vector2f(camTar.x, camTar.z)).lengthSquared() < 64 * 64)
                        continue;
                    if (chunkLod == 0 && haveRegion) {
                        for (int i = 0; i < 6; i++) {
                            var dir = VanillaUtils.DIRECTIONS[i];
                            if (!(dirToFace(dir, aabb, camPos).dot(dir.getUnitVec3f()) < 0)) continue;
                            var faceIndexGpuBuffer = storage.gpuBufferByFace.get(dir);
                            var faceIndexAlloc = faceIndexGpuBuffer.getAllocation(chunk.chunkPos);
                            if (faceIndexAlloc == null) continue;
                            var blockDataAlloc = storage.gpuBufferBlockData.getAllocation(chunk.chunkPos);
                            if (blockDataAlloc == null) continue;
                            var faceIndexBuffer = faceIndexGpuBuffer.getGpuBuffer(faceIndexAlloc);
                            var blockDataBuffer = storage.gpuBufferBlockData.getGpuBuffer(blockDataAlloc);
                            var offset = faceIndexAlloc.getOffsetFromHeap() / 4;
                            var size = IExtendedTlsfAllocation.cast(faceIndexAlloc).getX3dmap$requiedSize() / 4;
                            var cmd = new IndirectDrawCommand(6, (int) size, i * 6, 0, (int) offset);
                            gather2.add(blockDataBuffer, faceIndexBuffer, cmd);
                        }
                    } else {
                        var chunkPos = chunk.chunkPos;
                        var info = storage.terrainTextureManager.getUploadInfo(chunkPos);
                        var texture = storage.terrainTextureManager.getTextures(info.texturePos());
                        gather.add(texture, 0, new IndirectDrawCommand(TerrainRenderer.LODS[0].getIndexCount(), 1, 0, 0, 0, chunkPos.getMinBlockX(), chunkPos.getMinBlockZ()));
                    }
                }
            } else {
                var chunkPos = region.regionPos.toChunkPos();
                var info = storage.terrainTextureManager.getUploadInfo(chunkPos);
                var texture = storage.terrainTextureManager.getTextures(info.texturePos());
                gather.add(texture, lod, new IndirectDrawCommand(TerrainRenderer.LODS[lod].getIndexCount(), 1, 0, 0, 0, chunkPos.getMinBlockX(), chunkPos.getMinBlockZ()));
            }
            
        }
        return new RenderInfo(gather2.finishGather(), gather.finishGather());
    }
    
    public RenderInfoCompatible gatherRenderInfoCompatibleMode(Frustum frustum, boolean cullNear, Vector3f camPos, Vector3f camTar, int baseLodDistance) {
        var level = Minecraft.getInstance().level;
        if (level == null) return RenderInfoCompatible.empty();
        var storage = this.storageMap.get(level.dimension());
        if (storage == null) return RenderInfoCompatible.empty();
        assert storage.gpuBufferByLodFullMesh != null;
        var gather2 = new RenderInfoCompatibleBlockGather();
        for (var chunkStorage : storage.getChunks()) {
            var aabb = chunkStorage.aabb;
            if (!frustum.isVisible(aabb)) continue;
            if (cullNear && new Vector2f((float) Mth.lerp(0.5f, aabb.minX, aabb.maxX), (float) Mth.lerp(0.5f, aabb.minZ, aabb.maxZ)).sub(new Vector2f(camTar.x, camTar.z)).lengthSquared() < 64 * 64)
                continue;
            var lod = this.getLodLevel(chunkStorage.aabb.getCenter().toVector3f(), baseLodDistance, camPos);
            if (lod < 0) continue;
            if (lod == 0) {
                
                lod = 1;
            }
            var alloc = chunkStorage.getLodBufferFullMesh(lod);
            if (alloc == null) continue;
            var buffer = storage.gpuBufferByLodFullMesh.getGpuBuffer(alloc);
            var cmd = new IndirectDrawCommand(6 * chunkStorage.facesCountByLodFullMesh(lod), 1, (int) (alloc.getOffsetFromHeap() / 20), 0, 0);
            gather2.add(buffer, cmd);
            
        }
        return new RenderInfoCompatible(gather2.finishGather());
    }
    
    public RenderInfoCompatible gatherRenderInfoCompatibleModeMinimap(Frustum frustum, boolean cullNear, Vector3f camPos, Vector3f camTar, int highDetailRangeChunks) {
        var level = Minecraft.getInstance().level;
        if (level == null) return RenderInfoCompatible.empty();
        var storage = this.storageMap.get(level.dimension());
        if (storage == null) return RenderInfoCompatible.empty();
        assert storage.gpuBufferByLodFullMesh != null;
        var centerChunk = ChunkPos.containing(new BlockPos((int) camTar.x, (int) camTar.y, (int) camTar.z));
        var renderRange = 32;
        var highDetailRange = Math.min(highDetailRangeChunks, renderRange);
        var gather2 = new RenderInfoCompatibleBlockGather();
        for (var dx = -renderRange; dx <= renderRange; dx++) {
            for (var dz = -renderRange; dz <= renderRange; dz++) {
                var chunkStorage = storage.getChunk(new ChunkPos(centerChunk.x() + dx, centerChunk.z() + dz));
                if (chunkStorage == null) continue;
                var aabb = chunkStorage.aabb;
                if (!frustum.isVisible(aabb)) continue;
                if (cullNear && new Vector2f((float) Mth.lerp(0.5f, aabb.minX, aabb.maxX), (float) Mth.lerp(0.5f, aabb.minZ, aabb.maxZ)).sub(new Vector2f(camTar.x, camTar.z)).lengthSquared() < 64 * 64)
                    continue;
                var lod = Math.max(Math.abs(dx), Math.abs(dz)) <= highDetailRange ? 1 : 2;
                var alloc = chunkStorage.getLodBufferFullMesh(lod);
                if (alloc == null) continue;
                var buffer = storage.gpuBufferByLodFullMesh.getGpuBuffer(alloc);
                var cmd = new IndirectDrawCommand(6 * chunkStorage.facesCountByLodFullMesh(lod), 1, (int) (alloc.getOffsetFromHeap() / 20), 0, 0);
                gather2.add(buffer, cmd);
            }
        }
        return new RenderInfoCompatible(gather2.finishGather());
    }
    
    Vector3f dirToFace(Direction dir, AABB aabb, Vector3f pos) {
        var centerX = (float) (aabb.maxX + aabb.minX) / 2;
        var centerY = (float) (aabb.maxY + aabb.minY) / 2;
        var centerZ = (float) (aabb.maxZ + aabb.minZ) / 2;
        var center = switch (dir) {
            case DOWN -> new Vector3f(centerX, (float) aabb.maxY, centerZ);
            case UP -> new Vector3f(centerX, (float) aabb.minY, centerZ);
            case NORTH -> new Vector3f(centerX, centerY, (float) aabb.maxZ);
            case SOUTH -> new Vector3f(centerX, centerY, (float) aabb.minZ);
            case WEST -> new Vector3f((float) aabb.maxX, centerY, centerZ);
            case EAST -> new Vector3f((float) aabb.minX, centerY, centerZ);
        };
        return center.sub(pos, center).normalize();
    }
    
    public void submitUpdate(BlockPos center, int range, boolean force) {
        var centerChunk = ChunkPos.containing(center);
        for (var dx = -range; dx <= range; dx++) {
            for (var dz = -range; dz <= range; dz++) {
                this.submitUpdate(new ChunkPos(centerChunk.x() + dx, centerChunk.z() + dz), force);
            }
        }
    }
    
    public void submitTask(Runnable runnable) {
        this.taskQueue.submitWorker(runnable);
    }
    
    public void submitTaskOnMainThread(Runnable runnable) {
        this.taskQueue.submitMain(runnable);
    }
    
    public void submitUpdate(ChunkPos chunkPos, boolean force) {
        this.submitUpdate(null, chunkPos, force);
    }
    
    public void submitUpdate(@Nullable LevelChunk chunk, ChunkPos chunkPos, boolean force) {
        var level = Minecraft.getInstance().level;
        if (level == null || chunkPos == null) {
            return;
        }
        var dim = level.dimension();
        Runnable task = () -> {
            var level_ = Minecraft.getInstance().level;
            if (level_ == null) return;
            var dimNew = level_.dimension();
            if (dimNew != dim) return;
            var storage = this.storageMap.get(dimNew);
            if (storage == null) {
                LOGGER.debug("task in {} not in current dimension. did you just changed dimension?", chunkPos);
                return;
            }
            var chunkOld = storage.getChunk(chunkPos);
            if (chunkOld != null && !force) return;
            ChunkStorage chunkStorage;
            if (chunk == null) chunkStorage = LevelChunkStorage.COMPLIER.compile(storage, level_, chunkPos);
            else chunkStorage = LevelChunkStorage.COMPLIER.compile(storage, level_, chunk, chunkPos, true);
            if (chunkStorage != null) {
                this.submitTaskOnMainThread(() -> {
                    storage.putChunk(chunkStorage);
                    if (!compatibleMode) {
                        chunkStorage.uploadGpu0();
                        chunkStorage.uploadToTexture();
                    } else {
                        for (int dx = 0; dx < 2; dx++) {
                            for (int dz = 0; dz < 2; dz++) {
                                var cp = storage.getChunk(new ChunkPos(chunkPos.x() - dx, chunkPos.z() - dz));
                                if (cp == null) continue;
                                cp.uploadGpuLodFullMesh();
                            }
                        }
                    }
                    
                });
            }
        };
        this.taskQueue.submitWorker(task);
    }
    
    public void unloadLevel(@Nullable Level level) {
        if (level != null) {
            var storage = this.storageMap.get(level.dimension());
            if (storage != null) {
                storage.unloadGpu();
                storage.saveFile(false);
                this.mapPluginRegistry.closeLevel(level.dimension());
            }
            this.storageMap.remove(level.dimension());
        }
        this.taskQueue.clear();
    }
    
    
    @Override
    public void close() {
        for (var storage : this.storageMap.values()) {
            storage.unloadGpu();
        }
        this.mapPluginRegistry.closeRuntime();
        this.taskQueue.shutdown();
    }
    
    public long getMemAlloc() {
        var result = 0L;
        for (var storage : this.storageMap.values()) {
            for (var b : storage.getGpuBuffers()) {
                for (var p : b.nodes) {
                    result += p.getFirst().totalMemorySize;
                }
            }
            
        }
        return result;
    }
    
    public boolean canRegionResident(RegionPos regionPos) {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        float threshold = this.viewDistance + 256f * 1.41421356f;
        float px = (float) player.getX();
        float pz = (float) player.getZ();
        float regionCenterX = regionPos.x() * 512f + 256f;
        float regionCenterZ = regionPos.z() * 512f + 256f;
        float dx = regionCenterX - px;
        float dz = regionCenterZ - pz;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        return dist < threshold;
    }
    
    private void checkRegionResidency(LevelChunkStorage storage) {
        for (var region : storage.regionMap.values()) {
            if (canRegionResident(region.regionPos)) {
                if (!storage.residentRegions.contains(region.regionPos)) {
                    storage.residentRegions.add(region.regionPos);
                    if (region.haveNoDataChunk()) {
                        this.submitTask(() -> {
                            var file = region.getFile(storage.getDirectory());
                            var newRegionStorage = RegionStorage.loadFromFile(file, storage);
                            if (newRegionStorage == null) return;
                            for (var chunk : region.chunks()) {
                                this.submitTaskOnMainThread(() -> {
                                    if (chunk.state == ChunkStorage.State.NO_DATA) {
                                        var newChunk = newRegionStorage.getChunk(chunk.chunkPos);
                                        if (newChunk == null) return;
                                        chunk.writeData(newChunk.data);
                                    }
                                    if (storage.compatibleMode) {
                                        chunk.uploadGpuLodFullMesh();
                                    } else {
                                        chunk.uploadGpu0();
                                    }
                                    chunk.releaseData();
                                    chunk.state = ChunkStorage.State.ONLY_ON_GPU;
                                });
                            }
                        });
                    } else {
                        for (var chunk : region.chunks()) {
                            if (chunk.state == ChunkStorage.State.ONLY_ON_GPU) continue;
                            this.submitTaskOnMainThread(() -> {
                                if (storage.compatibleMode) {
                                    chunk.uploadGpuLodFullMesh();
                                } else {
                                    chunk.uploadGpu0();
                                }
                            });
                        }
                    }
                }
            } else {
                if (storage.residentRegions.contains(region.regionPos)) {
                    storage.residentRegions.remove(region.regionPos);
                    if (!region.haveDirtyChunk()) {
                        for (var chunk : region.chunks()) {
                            chunk.unloadGpu();
                            chunk.releaseData();
                            chunk.state = ChunkStorage.State.NO_DATA;
                        }
                    }
                }
            }
        }
    }
    
    public record RenderInfoCompatible(List<RenderInfoWithBufferBlock> lodFullMesh) implements AutoCloseable {
        
        public static RenderInfoCompatible empty() {
            return new RenderInfoCompatible(null);
        }
        
        @Override
        public void close() {
        }
    }
    
    public record RenderInfoWithBufferBlock(GpuBuffer drawBuffer, int drawCount,
                                            List<IndirectDrawCommand> drawCommands) {
        
    }
    
    public record RenderInfoWithFaceBlock(GpuBuffer blockDataBuffer, GpuBuffer faceIndexBuffer, int drawCount,
                                          GpuBuffer commandBuffer) {
        
    }
    
    public record RenderInfo(List<RenderInfoWithFaceBlock> blocks,
                             List<RenderInfoWithTextureBlock> lods) implements AutoCloseable {
        
        public static RenderInfo empty() {
            return new RenderInfo(null, null);
        }
        
        @Override
        public void close() {
            if(this.blocks != null) {
                for (var block : blocks) {
                    block.commandBuffer.close();
                }
            }
            if(this.lods != null) {
                for (var lod : lods) {
                    lod.commandBuffer.close();
                }
            }
        }
    }
    
    public record RenderInfoWithTextureBlock(TerrainTextureManager.VirtualTextures texture, int lod, int drawCount,
                                             GpuBuffer commandBuffer) {
        
    }
    
    public static class RenderInfoCompatibleBlockGather {
        public Map<GpuBuffer, ArrayList<IndirectDrawCommand>> cmdMap = new IdentityHashMap<>();
        
        public void add(GpuBuffer buffer, IndirectDrawCommand command) {
            cmdMap.compute(buffer, (_, v) -> {
                if (v == null) {
                    v = new ArrayList<>();
                }
                v.add(command);
                return v;
            });
        }
        
        public List<RenderInfoWithBufferBlock> finishGather() {
            var renderInfoList = new ArrayList<RenderInfoWithBufferBlock>();
            for (var entry : cmdMap.entrySet()) {
                var buffer = entry.getKey();
                var list = entry.getValue();
                renderInfoList.add(new RenderInfoWithBufferBlock(buffer, list.size(), list));
            }
            return renderInfoList;
        }
        
        public @Nullable RenderInfoWithBufferBlock finishGatherFirstBuffer() {
            var list = finishGather();
            return list.isEmpty() ? null : list.getFirst();
        }
    }
    
    public static class RenderInfoWithBufferBlockGather {
        public Map<GpuBuffer, Map<GpuBuffer, ArrayList<IndirectDrawCommand>>> cmdMap = new IdentityHashMap<>();
        
        public void add(GpuBuffer blockDataBuffer, GpuBuffer faceIndexBuffer, IndirectDrawCommand command) {
            var map = cmdMap.computeIfAbsent(blockDataBuffer, _ -> new IdentityHashMap<>());
            map.compute(faceIndexBuffer, (_, v) -> {
                if (v == null) {
                    v = new ArrayList<>();
                }
                v.add(command);
                return v;
            });
        }
        
        public List<RenderInfoWithFaceBlock> finishGather() {
            var renderInfoList = new ArrayList<RenderInfoWithFaceBlock>();
            for (var entry : cmdMap.entrySet()) {
                var blockDataBuffer = entry.getKey();
                for (var entry_ : entry.getValue().entrySet()) {
                    var faceIndexBuffer = entry_.getKey();
                    var list = entry_.getValue();
                    renderInfoList.add(new RenderInfoWithFaceBlock(blockDataBuffer, faceIndexBuffer, list.size(), IndirectDrawCommand.buildCommandList(list)));
                }
            }
            return renderInfoList;
        }
        
        public @Nullable RenderInfoWithFaceBlock finishGatherFirstBuffer() {
            var list = finishGather();
            return list.isEmpty() ? null : list.getFirst();
        }
    }
    
    public static class RenderInfoBlockGather {
        public Map<TerrainTextureManager.VirtualTextures, Multimap<Integer, IndirectDrawCommand>> cmdMap = new IdentityHashMap<>();
        
        public void add(TerrainTextureManager.VirtualTextures buffer, int lod, IndirectDrawCommand command) {
            cmdMap.compute(buffer, (_, v) -> {
                if (v == null) {
                    v = MultimapBuilder.hashKeys().arrayListValues().build();
                }
                v.put(lod, command);
                return v;
            });
        }
        
        public List<RenderInfoWithTextureBlock> finishGather() {
            var renderInfoList = new ArrayList<RenderInfoWithTextureBlock>();
            for (var entry : cmdMap.entrySet()) {
                var buffer = entry.getKey();
                var map = entry.getValue();
                for (var entry_ : map.asMap().entrySet()) {
                    renderInfoList.add(new RenderInfoWithTextureBlock(buffer, entry_.getKey(), entry_.getValue().size(), IndirectDrawCommand.buildCommandList(entry_.getValue())));
                }
                
            }
            return renderInfoList;
        }
        
    }
    
}
