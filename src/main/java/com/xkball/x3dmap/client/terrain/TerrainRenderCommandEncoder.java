package com.xkball.x3dmap.client.terrain;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.xkball.x3dmap.api.mixin.IExtendedTlsfAllocation;
import com.xkball.x3dmap.client.render.pip.layers.TerrainRenderer;
import com.xkball.xklibmc.client.b3d.IndirectDrawCommand;
import com.xkball.xklibmc.utils.VanillaUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("ClassCanBeRecord")
public class TerrainRenderCommandEncoder {
    
    public final TerrainChunkManager manager;
    
    public TerrainRenderCommandEncoder(TerrainChunkManager manager) {
        this.manager = manager;
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
    
    public RenderCommand gatherRenderInfo(Frustum frustum, boolean cullNear, Vector3f camPos, Vector3f camTar, int baseLodDistance) {
        var level = Minecraft.getInstance().level;
        if (level == null) return RenderCommand.empty();
        var storage = this.manager.storageMap.get(level.dimension());
        if (storage == null) return RenderCommand.empty();
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
        return new RenderCommand(gather2.finishGather(), gather.finishGather());
    }
    
    public RenderCommandCompatible gatherRenderInfoCompatibleMode(Frustum frustum, boolean cullNear, Vector3f camPos, Vector3f camTar, int baseLodDistance) {
        var level = Minecraft.getInstance().level;
        if (level == null) return RenderCommandCompatible.empty();
        var storage = this.manager.storageMap.get(level.dimension());
        if (storage == null) return RenderCommandCompatible.empty();
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
        return new RenderCommandCompatible(gather2.finishGather());
    }
    
    public RenderCommandCompatible gatherRenderInfoCompatibleModeMinimap(Frustum frustum, boolean cullNear, Vector3f camPos, Vector3f camTar, int highDetailRangeChunks) {
        var level = Minecraft.getInstance().level;
        if (level == null) return RenderCommandCompatible.empty();
        var storage = this.manager.storageMap.get(level.dimension());
        if (storage == null) return RenderCommandCompatible.empty();
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
        return new RenderCommandCompatible(gather2.finishGather());
    }
    
    public record RenderCommandWithBufferBlock(GpuBuffer drawBuffer, int drawCount,
                                               List<IndirectDrawCommand> drawCommands) {
        
    }
    
    public record RenderCommandWithFaceBlock(GpuBuffer blockDataBuffer, GpuBuffer faceIndexBuffer, int drawCount,
                                             GpuBuffer commandBuffer) {
        
    }
    
    public record RenderCommandWithTextureBlock(TerrainTextureManager.VirtualTextures texture, int lod, int drawCount,
                                                GpuBuffer commandBuffer) {
        
    }
    
    public record RenderCommandCompatible(List<RenderCommandWithBufferBlock> lodFullMesh) implements AutoCloseable {
        
        public static RenderCommandCompatible empty() {
            return new RenderCommandCompatible(null);
        }
        
        @Override
        public void close() {
        }
    }
    
    public record RenderCommand(@Nullable List<RenderCommandWithFaceBlock> blocks,
                                @Nullable List<RenderCommandWithTextureBlock> lods) implements AutoCloseable {
        
        public static RenderCommand empty() {
            return new RenderCommand(null, null);
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
        
        public List<RenderCommandWithBufferBlock> finishGather() {
            var renderInfoList = new ArrayList<RenderCommandWithBufferBlock>();
            for (var entry : cmdMap.entrySet()) {
                var buffer = entry.getKey();
                var list = entry.getValue();
                renderInfoList.add(new RenderCommandWithBufferBlock(buffer, list.size(), list));
            }
            return renderInfoList;
        }
        
        public TerrainRenderCommandEncoder.@Nullable RenderCommandWithBufferBlock finishGatherFirstBuffer() {
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
        
        public List<RenderCommandWithFaceBlock> finishGather() {
            var renderInfoList = new ArrayList<RenderCommandWithFaceBlock>();
            for (var entry : cmdMap.entrySet()) {
                var blockDataBuffer = entry.getKey();
                for (var entry_ : entry.getValue().entrySet()) {
                    var faceIndexBuffer = entry_.getKey();
                    var list = entry_.getValue();
                    renderInfoList.add(new RenderCommandWithFaceBlock(blockDataBuffer, faceIndexBuffer, list.size(), IndirectDrawCommand.buildCommandList(list)));
                }
            }
            return renderInfoList;
        }
        
        public TerrainRenderCommandEncoder.@Nullable RenderCommandWithFaceBlock finishGatherFirstBuffer() {
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
        
        public List<RenderCommandWithTextureBlock> finishGather() {
            var renderInfoList = new ArrayList<RenderCommandWithTextureBlock>();
            for (var entry : cmdMap.entrySet()) {
                var buffer = entry.getKey();
                var map = entry.getValue();
                for (var entry_ : map.asMap().entrySet()) {
                    renderInfoList.add(new RenderCommandWithTextureBlock(buffer, entry_.getKey(), entry_.getValue().size(), IndirectDrawCommand.buildCommandList(entry_.getValue())));
                }
                
            }
            return renderInfoList;
        }
        
    }
}
