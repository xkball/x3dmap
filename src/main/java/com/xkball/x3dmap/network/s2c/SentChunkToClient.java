package com.xkball.x3dmap.network.s2c;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.x3dmap.client.terrain.TerrainChunkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;


@NonNullByDefault
public record SentChunkToClient(ChunkPos chunkPos, ClientboundLevelChunkPacketData data, ClientboundChunksBiomesPacket biomeData) implements CustomPacketPayload{
    public static final CustomPacketPayload.Type<SentChunkToClient> TYPE = new CustomPacketPayload.Type<>(VanillaUtils.modRL("sent_chunk_to_client"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, SentChunkToClient> STREAM_CODEC = new StreamCodec<>() {
        
        @Override
        public SentChunkToClient decode(RegistryFriendlyByteBuf input) {
            var pos = ChunkPos.STREAM_CODEC.decode(input);
            var data = new ClientboundLevelChunkPacketData(input, pos.x(), pos.z());
            var biome = ClientboundChunksBiomesPacket.STREAM_CODEC.decode(input);
            return new SentChunkToClient(pos,  data, biome);
        }
        
        @Override
        public void encode(RegistryFriendlyByteBuf output, SentChunkToClient value) {
            ChunkPos.STREAM_CODEC.encode(output, value.chunkPos);
            value.data.write(output);
            ClientboundChunksBiomesPacket.STREAM_CODEC.encode(output, value.biomeData);
        }
    };
    
    public SentChunkToClient(ChunkPos chunkPos, LevelChunk chunk){
        this(chunkPos, new ClientboundLevelChunkPacketData(chunk), ClientboundChunksBiomesPacket.forChunks(List.of(chunk)));
    }
    
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
                var chunk = new LevelChunk(context.player().level(), this.chunkPos);
                chunk.replaceWithPacketData(this.data.getReadBuffer(), this.data.getHeightmaps(), this.data.getBlockEntitiesTagsConsumer(this.chunkPos.x(), this.chunkPos.z()));
                var data = biomeData.chunkBiomeData().getFirst();
                chunk.replaceBiomes(data.getReadBuffer());
                TerrainChunkManager.INSTANCE.submitUpdate(chunk, this.chunkPos, true);
        });
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
