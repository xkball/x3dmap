package com.xkball.x3dmap.client.terrain;

import com.mojang.blaze3d.vertex.TlsfAllocator;
import com.mojang.blaze3d.vertex.UberGpuBuffer;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklib.x3d.backend.vertex.BufferBuilder;
import com.xkball.xklib.x3d.backend.vertex.VertexFormat;
import com.xkball.xklib.x3d.backend.vertex.VertexFormats;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.utils.ClientUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.joml.GeometryUtils;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@NonNullByDefault
public class ChunkStorage {
    
    public final ChunkPos chunkPos;
    public final LevelChunkStorage levelStorage;
    public final ChunkStorageData data;
    /**
     * 虽然没有初始值, 但是通过文件加载或编译产生的对象, 这两个字段均不为null, 为null则说明产生非法状态
     */
    @SuppressWarnings("NotNullFieldNotInitialized")
    public AABB aabb;
    @SuppressWarnings("NotNullFieldNotInitialized")
    public ChunkHeightMap heightMap;
    public State state = State.NO_DATA;
    
    public ChunkStorage(ChunkPos chunkPos, LevelChunkStorage levelStorage) {
        this.chunkPos = chunkPos;
        this.levelStorage = levelStorage;
        this.data = new ChunkStorageData(chunkPos, new ArrayList<>());
    }
    
    public int facesCountByLodFullMesh(int lodLevel) {
        if (lodLevel == 1) return 64;
        if (lodLevel == 2) return 16;
        if (lodLevel == 3) return 4;
        return 0;
    }
    
    public TlsfAllocator.@Nullable Allocation getLodBufferFullMesh(int lodLevel) {
        if (lodLevel <= 3)
            return this.levelStorage.gpuBufferByLodFullMesh.getAllocation(new ChunkPosLod(this.chunkPos, lodLevel));
        return null;
    }
    
    public synchronized void writeData(List<ABlock.ABlockData> data) {
        this.data.data.clear();
        this.data.data.addAll(data);
        if (this.state == State.NO_DATA || this.state == State.ON_BOTH_SIDE) {
            this.state = State.DIRTY;
        } else if (this.state == State.ONLY_ON_GPU) {
            this.state = State.ON_BOTH_SIDE;
        }
    }
    
    public synchronized void releaseData() {
        this.data.data.clear();
        if (this.state == State.ON_BOTH_SIDE) {
            this.state = State.ONLY_ON_GPU;
        } else if (this.state == State.ONLY_ON_MEM) {
            this.state = State.NO_DATA;
        }
    }
    
    public void uploadGpuLodFullMesh() {
        removeFromUberBuffer(this.levelStorage.gpuBufferByLodFullMesh, new ChunkPosLod(this.chunkPos, 1));
        removeFromUberBuffer(this.levelStorage.gpuBufferByLodFullMesh, new ChunkPosLod(this.chunkPos, 2));
        removeFromUberBuffer(this.levelStorage.gpuBufferByLodFullMesh, new ChunkPosLod(this.chunkPos, 3));
        this.uploadGpuLodFullMesh(1, 2);
        this.uploadGpuLodFullMesh(2, 4);
        this.uploadGpuLodFullMesh(3, 8);
    }
    
    public void uploadToTexture() {
        this.levelStorage.terrainTextureManager.uploadChunk(this);
    }
    
    private void uploadGpuLodFullMesh(int lodLevel, int step) {
        var x0 = chunkPos.getMinBlockX();
        var z0 = chunkPos.getMinBlockZ();
        var minY = this.levelStorage.minHeight;
        try (var builder = BufferBuilder.start(VertexFormat.Mode.TRIANGLES, VertexFormats.POSITION_NORMAL_COLOR)) {
            for (int x = 0; x < 16; x += step) {
                for (int z = 0; z < 16; z += step) {
                    var h = this.heightMap.get(x, z);
                    if (h == minY) {
                        for (int i = 0; i < 6; i++) {
                            builder.addVertex(0, 0, 0).setNormal(0, 0, 0).setColor(0);
                        }
                        continue;
                    }
                    var c = this.heightMap.getColor(x, z);
                    var nx = x + step;
                    var nz = z + step;
                    int h01, h10, h11, c01, c10, c11;
                    if (nx < 16 && nz < 16) {
                        h01 = this.heightMap.get(nx, z);
                        c01 = this.heightMap.getColor(nx, z);
                        h10 = this.heightMap.get(x, nz);
                        c10 = this.heightMap.getColor(x, nz);
                        h11 = this.heightMap.get(nx, nz);
                        c11 = this.heightMap.getColor(nx, nz);
                    } else {
                        h01 = this.levelStorage.getHeight(x0 + nx, z0 + z);
                        c01 = this.levelStorage.getColor(x0 + nx, z0 + z);
                        h10 = this.levelStorage.getHeight(x0 + x, z0 + nz);
                        c10 = this.levelStorage.getColor(x0 + x, z0 + nz);
                        h11 = this.levelStorage.getHeight(x0 + nx, z0 + nz);
                        c11 = this.levelStorage.getColor(x0 + nx, z0 + nz);
                    }
                    if (h01 == minY) h01 = h;
                    if (h10 == minY) h10 = h;
                    if (h11 == minY) h11 = h;
                    var p0 = new Vector3f(x0 + x, h, z0 + z);
                    var p1 = new Vector3f(x0 + x, h10, z0 + nz);
                    var p2 = new Vector3f(x0 + nx, h01, z0 + z);
                    var p3 = new Vector3f(x0 + nx, h11, z0 + nz);
                    var normal = new Vector3f();
                    var normal1 = new Vector3f();
                    GeometryUtils.normal(p0, p1, p2, normal);
                    GeometryUtils.normal(p2, p1, p3, normal1);
                    builder.addVertex(p0).setNormal(normal).setColor(c);
                    builder.addVertex(p1).setNormal(normal).setColor(c10);
                    builder.addVertex(p2).setNormal(normal).setColor(c01);
                    builder.addVertex(p2).setNormal(normal1).setColor(c01);
                    builder.addVertex(p1).setNormal(normal1).setColor(c10);
                    builder.addVertex(p3).setNormal(normal1).setColor(c11);
                }
            }
            var buffer = builder.build();
            var gpuBuffer = this.levelStorage.gpuBufferByLodFullMesh;
            var success = gpuBuffer.addAllocation(new ChunkPosLod(this.chunkPos, lodLevel), (_) -> {
            }, buffer);
            if (!success) {
                gpuBuffer.uploadStagedAllocations(ClientUtils.getGpuDevice(), ClientUtils.getCommandEncoder());
                gpuBuffer.addAllocation(new ChunkPosLod(this.chunkPos, lodLevel), (_) -> {
                }, buffer);
            }
        }
    }
    
    public synchronized void uploadGpu0() {
        removeFromUberBuffer(this.levelStorage.gpuBufferBlockData, this.chunkPos);
        for (var b : this.levelStorage.gpuBufferByFace.values()) {
            removeFromUberBuffer(b, this.chunkPos);
        }
        if(this.data.data.isEmpty()) return;
        var l1List = new ArrayList<ABlock>();
        var x0 = chunkPos.getMinBlockX();
        var z0 = chunkPos.getMinBlockZ();
        for (var b : this.data.data()) {
            l1List.add(b.toABlock(x0, z0));
        }
        var blockDataBuffer = MemoryUtil.memAlloc(l1List.size() * 16);
        for (var ab : l1List) {
            blockDataBuffer.putFloat(ab.x());
            blockDataBuffer.putFloat(ab.y());
            blockDataBuffer.putFloat(ab.z());
            blockDataBuffer.putInt(ab.color());
        }
        blockDataBuffer.flip();
        var blockDataGpuBuffer = this.levelStorage.gpuBufferBlockData;
        blockDataGpuBuffer.uploadStagedAllocations(ClientUtils.getGpuDevice(), ClientUtils.getCommandEncoder());
        blockDataGpuBuffer.addAllocation(this.chunkPos, (_) -> {}, blockDataBuffer);
        blockDataGpuBuffer.uploadStagedAllocations(ClientUtils.getGpuDevice(), ClientUtils.getCommandEncoder());
        var blockDataAlloc = blockDataGpuBuffer.getAllocation(this.chunkPos);
        var blockDataBaseIndex = (int) (Objects.requireNonNull(blockDataAlloc).getOffsetFromHeap() / 16);
        MemoryUtil.memFree(blockDataBuffer);
        for (var dir : VanillaUtils.DIRECTIONS) {
            var list = new ArrayList<Integer>();
            for (int i = 0; i < l1List.size(); i++) {
                var a = l1List.get(i);
                if ((a.mask() & (1 << dir.get3DDataValue())) > 0) {
                    list.add(blockDataBaseIndex + i);
                }
            }
            if(list.isEmpty()) continue;
            var buffer = MemoryUtil.memAlloc(list.size() * 4);
            for (var index : list) {
                buffer.putInt(index);
            }
            buffer.flip();
            var gpuBuffer = this.levelStorage.gpuBufferByFace.get(dir);
            var success = gpuBuffer.addAllocation(this.chunkPos, (_) -> {}, buffer);
            if (!success) {
                gpuBuffer.uploadStagedAllocations(ClientUtils.getGpuDevice(), ClientUtils.getCommandEncoder());
                gpuBuffer.addAllocation(this.chunkPos, (_) -> {}, buffer);
            }
            MemoryUtil.memFree(buffer);
        }
    }
    
    public void unloadGpu() {
        removeFromUberBuffer(this.levelStorage.gpuBufferBlockData, this.chunkPos);
        for (var b : this.levelStorage.gpuBufferByFace.values()) {
            removeFromUberBuffer(b, this.chunkPos);
        }
        removeFromUberBuffer(this.levelStorage.gpuBufferByLodFullMesh, new ChunkPosLod(this.chunkPos, 1));
        removeFromUberBuffer(this.levelStorage.gpuBufferByLodFullMesh, new ChunkPosLod(this.chunkPos, 2));
        removeFromUberBuffer(this.levelStorage.gpuBufferByLodFullMesh, new ChunkPosLod(this.chunkPos, 3));
        
    }
    
    private static <T> void removeFromUberBuffer(UberGpuBuffer<T> buffer, T key) {
        if (buffer.getAllocation(key) == null) return;
        buffer.removeAllocation(key);
        buffer.uploadStagedAllocations(ClientUtils.getGpuDevice(), ClientUtils.getCommandEncoder());
    }
    
    public record ChunkStorageData(ChunkPos pos, List<ABlock.ABlockData> data) {
        
        public static final StreamCodec<ByteBuf, ChunkStorageData> STREAM_CODEC = StreamCodec.composite(
                ChunkPos.STREAM_CODEC,
                ChunkStorageData::pos,
                ByteBufCodecs.collection(ArrayList::new, ABlock.ABlockData.STREAM_CODEC),
                ChunkStorageData::data,
                ChunkStorageData::new
        );
    }
    
    public enum State {
        /**
         * 刚编译出chunk, 此时数据双方都有
         */
        DIRTY,
        ONLY_ON_GPU,
        /**
         * 刚从硬盘加载chunk
         */
        ONLY_ON_MEM,
        ON_BOTH_SIDE,
        NO_DATA
    }
}
