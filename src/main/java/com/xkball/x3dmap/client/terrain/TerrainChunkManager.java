package com.xkball.x3dmap.client.terrain;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.ClientConfig;
import com.xkball.x3dmap.X3dMapClient;
import com.xkball.x3dmap.client.map.compatibility.CompatibilityExtension;
import com.xkball.x3dmap.client.map.plugin.X3dMapPluginRegistry;
import com.xkball.x3dmap.utils.DualQueueThreadPool;
import com.xkball.x3dmap.utils.X3dClientUtils;
import com.xkball.xklibmc.XKLibMCClient;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.api.client.b3d.ICloseOnExit;
import com.xkball.xklibmc.utils.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@EventBusSubscriber(Dist.CLIENT)
@NonNullByDefault
public class TerrainChunkManager implements ICloseOnExit<TerrainChunkManager> {
    
    public static final TerrainChunkManager INSTANCE = new TerrainChunkManager();
    private static final Logger LOGGER = LogUtils.getLogger();
    public final DualQueueThreadPool taskQueue = new DualQueueThreadPool();
    public final X3dMapPluginRegistry mapPluginRegistry = new X3dMapPluginRegistry();
    private final ArrayDeque<ChunkPos> updateQueue = new ArrayDeque<>();
    private final Set<ChunkPos> updateChunkSet = new HashSet<>();
    private final TerrainRenderCommandEncoder commandEncoder = new TerrainRenderCommandEncoder(this);
    public boolean compatibleMode = false;
    public List<String> compatibilityReasons = Collections.emptyList();
    public boolean compatibilityWarningSuppressed = false;
    public int viewDistance = 1024;
    public @Nullable ResourceKey<Level> currentLevel;
    public @Nullable LevelChunkStorage currentChunkStorage;
    
    public TerrainChunkManager() {
    
    }
    
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        INSTANCE.tick();
    }
    
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        var level = event.getLevel();
        if (!level.isClientSide()) return;
        var chunkPos = event.getChunk().getPos();
        if (X3dClientUtils.isClientChunkAroundLoaded(chunkPos)) {
            INSTANCE.enqueueUpdate(event.getChunk().getPos());
        }
        int x = chunkPos.x();
        int z = chunkPos.z();
        var storage = INSTANCE.getCurrentLevelChunkStorage();
        if (storage == null) return;
        if (X3dClientUtils.isClientChunkAroundLoaded(chunkPos)) {
            INSTANCE.enqueueUpdate(new ChunkPos(x - 1, z));
        }
        if (X3dClientUtils.isClientChunkAroundLoaded(chunkPos)) {
            INSTANCE.enqueueUpdate(new ChunkPos(x + 1, z));
        }
        if (X3dClientUtils.isClientChunkAroundLoaded(chunkPos)) {
            INSTANCE.enqueueUpdate(new ChunkPos(x, z - 1));
        }
        if (X3dClientUtils.isClientChunkAroundLoaded(chunkPos)) {
            INSTANCE.enqueueUpdate(new ChunkPos(x, z + 1));
        }
    }
    
    @SubscribeEvent
    public static void onClientLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        INSTANCE.unloadCurrentLevel();
        INSTANCE.mapPluginRegistry.closeRuntime();
    }
    
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel clientLevel && Objects.equals(INSTANCE.currentLevel, clientLevel.dimension()))
            INSTANCE.unloadCurrentLevel();
    }
    
    public void tick() {
        if (!Minecraft.getInstance().isPaused() && Minecraft.getInstance().level != null) {
            this.tryLoadLevel(Minecraft.getInstance().level);
            this.taskQueue.runFor10ms();
            if (this.currentChunkStorage != null) {
                for (var b : this.currentChunkStorage.getGpuBuffers()) {
                    if (!b.stagedAllocations.isEmpty()) {
                        b.uploadStagedAllocations(ClientUtils.getGpuDevice(), ClientUtils.getCommandEncoder());
                    }
                }
            }
        }
        if (XKLibMCClient.tickCount % 100 == 0 && this.currentChunkStorage != null) {
            this.checkRegionResidency(this.currentChunkStorage);
        }
        int drawInterval = ClientConfig.DRAW_NEW_CHUNK_INTERVAL.get();
        if (drawInterval > 0 && XKLibMCClient.tickCount % drawInterval == 0) {
            this.processUpdateQueue(ClientConfig.DRAW_NEW_CHUNK_COUNT.get());
        }
        int saveInterval = ClientConfig.AUTO_SAVE_INTERVAL.get();
        if (saveInterval > 0 && XKLibMCClient.tickCount % saveInterval == 0) {
            if (this.currentChunkStorage != null) {
                this.currentChunkStorage.saveFile(true);
            }
            this.mapPluginRegistry.saveData();
        }
    }
    
    public void enqueueUpdate(ChunkPos chunkPos) {
        if (!updateChunkSet.contains(chunkPos)) {
            updateQueue.add(chunkPos);
            updateChunkSet.add(chunkPos);
        }
    }
    
    private void processUpdateQueue(int count) {
        if (X3dMapClient.loading) return;
        for (int i = 0; i < count && !updateQueue.isEmpty(); i++) {
            var chunkPos = updateQueue.pollFirst();
            updateChunkSet.remove(chunkPos);
            this.submitUpdate(chunkPos, true);
        }
    }
    
    public void initializeMapApi() {
        this.mapPluginRegistry.initialize(this);
    }
    
    public void tryLoadLevel(@Nullable Level level) {
        if (level == null || Objects.equals(this.currentLevel, level.dimension())) return;
        this.setCloseOnExit();
        this.unloadCurrentLevel();
        this.initializeMapApi();
        CompatibilityExtension.initCompatibilityMode();
        this.mapPluginRegistry.openRuntime(X3dClientUtils.getEncodedSaveOrServerName());
        this.currentLevel = level.dimension();
        var s = new LevelChunkStorage(level.dimension(), level.getMinY(), level.getMaxY(), this.compatibleMode);
        this.mapPluginRegistry.openLevel(s.dimension, s.getDirectory());
        s.loadFile();
        this.currentChunkStorage = s;
        
    }
    
    public @Nullable LevelChunkStorage getCurrentLevelChunkStorage() {
        return this.currentChunkStorage;
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
            if (!Objects.equals(this.currentLevel, dim)) return;
            var storage = this.currentChunkStorage;
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
    
    public void unloadCurrentLevel() {
        if (this.currentChunkStorage != null && this.currentLevel != null) {
            this.currentChunkStorage.unloadGpu();
            this.currentChunkStorage.saveFile(false);
            this.mapPluginRegistry.closeLevel(this.currentLevel);
            this.currentLevel = null;
            this.currentChunkStorage = null;
        }
        this.taskQueue.clear();
    }
    
    
    @Override
    public void close() {
        if (this.currentChunkStorage != null) {
            this.currentChunkStorage.unloadGpu();
        }
        this.mapPluginRegistry.closeRuntime();
        this.taskQueue.shutdown();
    }
    
    public long getMemAlloc() {
        var result = 0L;
        if (this.currentChunkStorage != null) {
            for (var b : this.currentChunkStorage.getGpuBuffers()) {
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
    
    
    public TerrainRenderCommandEncoder getTerrainCommandEncoder() {
        return commandEncoder;
    }
}
