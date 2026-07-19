package com.xkball.x3dmap.client.terrain;

import com.mojang.blaze3d.GraphicsWorkarounds;
import com.mojang.blaze3d.vertex.UberGpuBuffer;
import com.xkball.x3dmap.X3dMapClient;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.utils.ClientUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;
import org.jspecify.annotations.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@NonNullByDefault
public class LevelChunkStorage {
    
    public static final int VERSION = 3;
    public static final ChunkComplier COMPLIER = new ChunkComplier();
    
    public final int minHeight;
    public final int maxHeight;
    public final String saveName;
    public final boolean compatibleMode;
    public final ResourceKey<Level> dimension;
    public @Nullable UberGpuBuffer<ChunkPos> gpuBufferBlockData;
    public EnumMap<Direction, UberGpuBuffer<ChunkPos>> gpuBufferByFace = new EnumMap<>(Direction.class);
    public @Nullable UberGpuBuffer<ChunkPosLod> gpuBufferByLodFullMesh;
    public TerrainTextureManager terrainTextureManager = new TerrainTextureManager(this);
    private final List<UberGpuBuffer<?>> gpuBuffers = new ArrayList<>();
    public final Map<RegionPos, RegionStorage> regionMap = new LinkedHashMap<>();
    public final Set<RegionPos> residentRegions = new LinkedHashSet<>();
    
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
        if(!compatibleMode){
            this.gpuBufferBlockData = new UberGpuBuffer<>("terrain_block_data", 64, 64 * 1024 * 1024, 16, gpuDevice, 8 * 1024 * 1024, gpuWorkaround);
            this.gpuBuffers.add(this.gpuBufferBlockData);
            for (var dir : VanillaUtils.DIRECTIONS) {
                this.gpuBufferByFace.put(dir, new UberGpuBuffer<>("terrain_" + dir + "_index", 64, 32 * 1024 * 1024, 4, gpuDevice, 8 * 1024 * 1024, gpuWorkaround));
            }
            this.gpuBuffers.addAll(gpuBufferByFace.values());
        }
        else {
            this.gpuBufferByLodFullMesh = new UberGpuBuffer<>("terrain_lod", 32, 64 * 1024 * 1024, 20/*DefaultVertexFormat.POSITION_COLOR_NORMAL.getVertexSize()*/, gpuDevice, 8 * 1024 * 1024, gpuWorkaround);
            this.gpuBuffers.add(gpuBufferByLodFullMesh);
        }
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
                        if (!compatibleMode) {
                            chunkStorage.uploadToTexture();
                        } ;
                    });
                }
                TerrainChunkManager.INSTANCE.submitTaskOnMainThread(() -> X3dMapClient.loading = false);
            });
        }, TerrainChunkManager.INSTANCE.taskQueue.workers);
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
    
    public boolean containsChunk(int chunkX, int chunkZ) {
        return containsChunk(new ChunkPos(chunkX, chunkZ));
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
    
}
