package com.xkball.x3dmap.client.terrain;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.ClientConfig;
import com.xkball.x3dmap.X3dMapClient;
import com.xkball.x3dmap.client.map.compatibility.CompatibilityExtension;
import com.xkball.x3dmap.client.map.plugin.X3dMapPluginRegistry;
import com.xkball.x3dmap.client.terrain.file.MapChunk;
import com.xkball.x3dmap.client.terrain.file.MapLevel;
import com.xkball.x3dmap.utils.X3dClientUtils;
import com.xkball.xklibmc.XKLibMCClient;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.api.client.b3d.ICloseOnExit;
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
import net.neoforged.fml.loading.FMLPaths;
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
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@EventBusSubscriber(Dist.CLIENT)
@NonNullByDefault
public class TerrainChunkManager implements ICloseOnExit<TerrainChunkManager> {
    
    public static final TerrainChunkManager INSTANCE = new TerrainChunkManager();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ChunkComplier COMPLIER = new ChunkComplier();
    public final X3dMapPluginRegistry mapPluginRegistry = new X3dMapPluginRegistry();
    private final ArrayDeque<ChunkPos> updateQueue = new ArrayDeque<>();
    private final Set<ChunkPos> updateChunkSet = new HashSet<>();
    public boolean compatibleMode = false;
    public List<String> compatibilityReasons = Collections.emptyList();
    public boolean compatibilityWarningSuppressed = false;
    public @Nullable ResourceKey<Level> currentLevel;
    public @Nullable MapLevel currentChunkStorage;
    
    
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
        }
        int drawInterval = ClientConfig.DRAW_NEW_CHUNK_INTERVAL.get();
        if (drawInterval > 0 && XKLibMCClient.tickCount % drawInterval == 0) {
            this.processUpdateQueue(ClientConfig.DRAW_NEW_CHUNK_COUNT.get());
        }
        int saveInterval = ClientConfig.AUTO_SAVE_INTERVAL.get();
        if (saveInterval > 0 && XKLibMCClient.tickCount % saveInterval == 0) {
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
        var dimension = level.dimension().identifier();
        var dir = FMLPaths.GAMEDIR.get()
                .resolve("x3dmap")
                .resolve(X3dClientUtils.getEncodedSaveOrServerName())
                .resolve(dimension.getNamespace())
                .resolve(dimension.getPath());
        this.mapPluginRegistry.openLevel(level.dimension(), dir);
        this.currentChunkStorage = new MapLevel(level, dir, X3dMapClient.mainThreadExecutor, X3dMapClient.taskExecutor);
        
    }
    
    public @Nullable MapLevel getCurrentLevelChunkStorage() {
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
        X3dMapClient.taskExecutor.execute(runnable);
    }
    
    public void submitTaskOnMainThread(Runnable runnable) {
        X3dMapClient.mainThreadExecutor.execute(runnable);
    }
    
    public void submitUpdate(ChunkPos chunkPos, boolean force) {
        this.submitUpdate(null, chunkPos, force);
    }
    
    public void submitUpdate(@Nullable LevelChunk chunk, ChunkPos chunkPos, boolean force) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
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
            if (!force && storage.containsChunk(chunkPos)) return;
            MapChunk mapChunk;
            if (chunk == null) mapChunk = COMPLIER.compile(level_, chunkPos);
            else mapChunk = COMPLIER.compile(level_, chunk, chunkPos, true);
            if (mapChunk != null) {
                storage.updateChunk(mapChunk);
            }
        };
        X3dMapClient.taskExecutor.execute(task);
    }
    
    public void unloadCurrentLevel() {
        if (this.currentChunkStorage != null && this.currentLevel != null) {
            this.currentChunkStorage.close();
            this.mapPluginRegistry.closeLevel(this.currentLevel);
            this.currentLevel = null;
            this.currentChunkStorage = null;
        }
    }
    
    
    @Override
    public void close() {
        if (this.currentChunkStorage != null) {
            this.currentChunkStorage.close();
            this.currentChunkStorage = null;
            this.currentLevel = null;
        }
        this.mapPluginRegistry.closeRuntime();
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



}
