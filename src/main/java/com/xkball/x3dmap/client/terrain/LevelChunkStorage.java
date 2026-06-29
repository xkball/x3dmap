package com.xkball.x3dmap.client.terrain;

import com.mojang.blaze3d.GraphicsWorkarounds;
import com.mojang.blaze3d.vertex.UberGpuBuffer;
import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.X3dMapClient;
import com.xkball.x3dmap.api.client.map.WorldMapExtensionStorage;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.utils.ClientUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class LevelChunkStorage {
    
    public static final int VERSION = 2;
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ChunkComplier COMPLIER = new ChunkComplier();
    
    public final int minHeight;
    public final int maxHeight;
    public final String saveName;
    public final boolean compatibleMode;
    public final ResourceKey<Level> dimension;
    public UberGpuBuffer<ChunkPos> gpuBufferBlockData;
    public EnumMap<Direction, UberGpuBuffer<ChunkPos>> gpuBufferByFace = new EnumMap<>(Direction.class);
    public UberGpuBuffer<ChunkPosLod> gpuBufferByLodFullMesh;
    public TerrainTextureManager terrainTextureManager = new TerrainTextureManager(this);
    private final List<UberGpuBuffer<?>> gpuBuffers = new ArrayList<>();
    public final Map<RegionPos, RegionStorage> regionMap = new LinkedHashMap<>();
    public final Set<RegionPos> residentRegions = new LinkedHashSet<>();
    private final Map<String, WorldMapExtensionStorage> extensionStorageMap = new LinkedHashMap<>();
    
    public LevelChunkStorage(ResourceKey<Level> dimension, int minHeight, int maxHeight, boolean compatibleMode) {
        this.dimension = dimension;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.compatibleMode = compatibleMode;
        this.saveName = ClientUtils.getSaveOrServerName();
        this.createBuffer();
    }
    
    public void createBuffer() {
        this.unloadGpu();
        this.gpuBuffers.clear();
        var gpuDevice = ClientUtils.getGpuDevice();
        var gpuWorkaround = GraphicsWorkarounds.get(gpuDevice);
        this.gpuBufferBlockData = new UberGpuBuffer<>("terrain_block_data", 64, 64 * 1024 * 1024, 16, gpuDevice, 8 * 1024 * 1024, gpuWorkaround);
        this.gpuBuffers.add(this.gpuBufferBlockData);
        for (var dir : VanillaUtils.DIRECTIONS) {
            this.gpuBufferByFace.put(dir, new UberGpuBuffer<>("terrain_" + dir + "_index", 64, 64 * 1024 * 1024, 4, gpuDevice, 8 * 1024 * 1024, gpuWorkaround));
        }
        this.gpuBufferByLodFullMesh = new UberGpuBuffer<>("terrain_lod", 32, 64 * 1024 * 1024, 20/*DefaultVertexFormat.POSITION_COLOR_NORMAL.getVertexSize()*/, gpuDevice, 8 * 1024 * 1024, gpuWorkaround);
        this.gpuBuffers.addAll(gpuBufferByFace.values());
        this.gpuBuffers.add(gpuBufferByLodFullMesh);
    }
    
    public List<UberGpuBuffer<?>> getGpuBuffers() {
        return this.gpuBuffers;
    }
    
    public void deleteChunk(ChunkPos chunkPos) {
        var region = this.getRegion(RegionStorage.toRegionPos(chunkPos));
        if (region != null) {
            region.deleteChunk(chunkPos);
            if (!this.compatibleMode) {
                this.terrainTextureManager.clearChunk(chunkPos);
            }
        }
    }
    
    public void registerExtensionStorage(WorldMapExtensionStorage storage) {
        this.extensionStorageMap.put(storage.extensionId(), storage);
    }
    
    public @Nullable WorldMapExtensionStorage getExtensionStorage(String extensionId) {
        return this.extensionStorageMap.get(extensionId);
    }
    
    public Path getExtensionFile(String extensionId) {
        return this.getDirectory().resolve(extensionId);
    }
    
    public void unloadGpu() {
        for (var b : this.gpuBuffers) {
            b.close();
        }
        this.terrainTextureManager.close();
    }
    
    public void saveFile(boolean async) {
        for (var entry : this.regionMap.entrySet()) {
            if (entry.getValue().haveDirtyChunk()) {
                if (async) {
                    TerrainChunkManager.INSTANCE.taskQueue.submitAsyncIgnoreMain(() -> this.saveRegion(entry.getKey()));
                } else {
                    this.saveRegion(entry.getKey());
                }
            }
        }
        this.saveExtensionFiles();
    }
    
    public int getHeight(int x, int z) {
        var chunkPos = new ChunkPos(x >> 4, z >> 4);
        var chunk = this.getChunk(chunkPos);
        if (chunk == null) return -64;
        return chunk.heightMap.get(x, z);
    }
    
    public int getColor(int x, int z) {
        var chunkPos = new ChunkPos(x >> 4, z >> 4);
        var chunk = this.getChunk(chunkPos);
        if (chunk == null) return 0;
        return chunk.heightMap.getColor(x, z);
    }
    
    public void loadFile() {
        var dir = this.getDirectory().toFile();
        if (!dir.exists() || !dir.isDirectory()) return;
        var files = dir.listFiles();
        if (files == null || files.length == 0) return;
        X3dMapClient.loading = true;
        var taskList = new ArrayList<CompletableFuture<Void>>();
        for (var file : files) {
            taskList.add(CompletableFuture.runAsync(() -> {
                var regionStorage = RegionStorage.loadFromFile(file.toPath(), this);
                if (regionStorage == null) {
                    return;
                }
                TerrainChunkManager.INSTANCE.submitTaskOnMainThread(() -> {
                    this.regionMap.put(regionStorage.regionPos, regionStorage);
                    if (TerrainChunkManager.INSTANCE.canRegionResident(regionStorage.regionPos)) {
                        for (var chunkStorage : regionStorage.chunks()) {
                            TerrainChunkManager.INSTANCE.submitTaskOnMainThread(() -> {
                                if (!compatibleMode) {
                                    chunkStorage.uploadGpu0();
                                } else chunkStorage.uploadGpuLodFullMesh();
                                if (chunkStorage.state == ChunkStorage.State.ONLY_ON_MEM) {
                                    chunkStorage.state = ChunkStorage.State.ON_BOTH_SIDE;
                                }
                            });
                        }
                    } else {
                        for (var chunkStorage : regionStorage.chunks()) {
                            if (chunkStorage.state == ChunkStorage.State.ONLY_ON_MEM) {
                                chunkStorage.releaseData();
                                chunkStorage.state = ChunkStorage.State.NO_DATA;
                            }
                        }
                    }
                });
            }, TerrainChunkManager.INSTANCE.taskQueue.workers));
            
        }
        var task = CompletableFuture.allOf(taskList.toArray(CompletableFuture[]::new));
        task.thenRunAsync(() -> {
            TerrainChunkManager.INSTANCE.submitTaskOnMainThread(() -> {
                for (var chunkStorage : this.getChunks()) {
                    TerrainChunkManager.INSTANCE.submitTaskOnMainThread(() -> {
                        if (!this.containsChunk(chunkStorage.chunkPos)) return;
                        if (compatibleMode) {
                            chunkStorage.uploadGpuLodFullMesh();
                        } else chunkStorage.uploadToTexture();
                    });
                }
                TerrainChunkManager.INSTANCE.submitTaskOnMainThread(() -> X3dMapClient.loading = false);
            });
        }, TerrainChunkManager.INSTANCE.taskQueue.workers);
        this.loadExtensionFiles();
    }
    
    public void saveRegion(RegionPos regionPos) {
        var regionStorage = this.regionMap.get(regionPos);
        if (regionStorage == null) {
            return;
        }
        regionStorage.saveToFile(this.getDirectory(), this);
    }
    
    public RegionStorage getOrCreateRegion(RegionPos regionPos) {
        return this.regionMap.computeIfAbsent(regionPos, rp -> new RegionStorage(rp, this.minHeight, this.maxHeight));
    }
    
    public RegionStorage getRegion(RegionPos regionPos) {
        return this.regionMap.get(regionPos);
    }
    
    public @Nullable ChunkStorage getChunk(ChunkPos chunkPos) {
        var region = this.getRegion(RegionStorage.toRegionPos(chunkPos));
        if (region == null) {
            return null;
        }
        return region.getChunk(chunkPos);
    }
    
    public boolean containsChunk(ChunkPos chunkPos) {
        return this.getChunk(chunkPos) != null;
    }
    
    public void putChunk(ChunkStorage chunkStorage) {
        this.getOrCreateRegion(RegionStorage.toRegionPos(chunkStorage.chunkPos)).putChunk(chunkStorage);
    }
    
    public List<ChunkStorage> getChunks() {
        var list = new ArrayList<ChunkStorage>();
        for (var regionStorage : this.regionMap.values()) {
            list.addAll(regionStorage.chunks());
        }
        return list;
    }
    
    public Path getDirectory() {
        var dim = dimension.identifier();
        return FMLPaths.GAMEDIR.get().resolve("x3dmap").resolve(this.saveName).resolve(dim.getNamespace()).resolve(dim.getPath());
    }
    
    private boolean hasDirtyExtensionStorage() {
        for (var storage : this.extensionStorageMap.values()) {
            if (storage.dirty()) {
                return true;
            }
        }
        return false;
    }
    
    private void loadExtensionFiles() {
        for (var storage : this.extensionStorageMap.values()) {
            var file = this.getExtensionFile(storage.extensionId());
            if (!file.toFile().exists()) {
                continue;
            }
            try {
                var bytes = Files.readAllBytes(file);
                bytes = VanillaUtils.unGzip(bytes);
                var byteBuf = Unpooled.buffer(bytes.length);
                byteBuf.writeBytes(bytes);
                storage.load(byteBuf);
                storage.clearDirty();
            } catch (Exception e) {
                LOGGER.error("Failed to load map extension storage {}", storage.extensionId(), e);
            }
        }
    }
    
    private void saveExtensionFiles() {
        for (var storage : this.extensionStorageMap.values()) {
            if (!storage.dirty()) {
                continue;
            }
            var file = this.getExtensionFile(storage.extensionId()).toFile();
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    LOGGER.error("Failed to create map extension storage file {}", file.getAbsolutePath(), e);
                    throw new RuntimeException(e);
                }
            }
            try (var output = new FileOutputStream(file)) {
                var byteBuf = Unpooled.buffer();
                storage.save(byteBuf);
                output.write(VanillaUtils.gzip(byteBuf.array(), 0, byteBuf.readableBytes()));
                storage.clearDirty();
            } catch (IOException e) {
                LOGGER.error("Failed to save map extension storage file {}", file.getAbsolutePath(), e);
                throw new RuntimeException(e);
            }
        }
    }
    
}
