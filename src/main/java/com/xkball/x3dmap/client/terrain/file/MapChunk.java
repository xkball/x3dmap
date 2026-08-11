package com.xkball.x3dmap.client.terrain.file;

import com.xkball.x3dmap.client.terrain.ChunkStorage;
import com.xkball.x3dmap.client.terrain.CompressedChunkCoordDataMap;
import com.xkball.x3dmap.utils.CodecUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

@NonNullByDefault
public class MapChunk {
    
    public static final StreamCodec<ByteBuf, CompressedChunkCoordDataMap<ChunkStorage.TerrainBlockData>> TERRAIN_BLOCK_DATA_STREAM_CODEC = CompressedChunkCoordDataMap.streamCodec(ChunkStorage.TerrainBlockData.STREAM_CODEC);
    public static final StreamCodec<ByteBuf, MapChunk> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MapChunk decode(ByteBuf input) {
            var chunkpos = ChunkPos.STREAM_CODEC.decode(input);
            var aabb = CodecUtils.AABB_STREAM_CODEC.decode(input);
            var data = TERRAIN_BLOCK_DATA_STREAM_CODEC.decode(input);
            return new MapChunk(chunkpos, data, aabb, FileChunkState.NORMAL);
        }
        
        @Override
        public void encode(ByteBuf output, MapChunk value) {
            ChunkPos.STREAM_CODEC.encode(output, value.chunkPos);
            CodecUtils.AABB_STREAM_CODEC.encode(output, value.aabb);
            TERRAIN_BLOCK_DATA_STREAM_CODEC.encode(output, value.data);
        }
    };
    
    public final ChunkPos chunkPos;
    public CompressedChunkCoordDataMap<ChunkStorage.TerrainBlockData> data;
    public AABB aabb ;
    public FileChunkState state = FileChunkState.EMPTY;
    
    public MapChunk(ChunkPos chunkPos) {
        this.chunkPos = chunkPos;
        this.data = new CompressedChunkCoordDataMap<>(chunkPos);
        this.aabb = new AABB(0,0,0,0,0,0);
    }
    
    public MapChunk(ChunkPos chunkPos, CompressedChunkCoordDataMap<ChunkStorage.TerrainBlockData> data, AABB aabb, FileChunkState state) {
        this.chunkPos = chunkPos;
        this.data = data;
        this.aabb = aabb;
        this.state = state;
    }
    
}
