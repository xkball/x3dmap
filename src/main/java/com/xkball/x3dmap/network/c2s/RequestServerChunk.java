package com.xkball.x3dmap.network.c2s;

import com.xkball.x3dmap.X3dMap;
import com.xkball.x3dmap.network.s2c.SentChunkToClient;
import com.xkball.x3dmap.server.ChunkBatcher;
import com.xkball.x3dmap.server.ChunkRequest;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@NonNullByDefault
public record RequestServerChunk(List<ChunkPos> pos, boolean generate) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<RequestServerChunk> TYPE = new Type<>(VanillaUtils.modRL("request_server_chunk"));
    
    public static final StreamCodec<ByteBuf, RequestServerChunk> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ChunkPos.STREAM_CODEC),
            RequestServerChunk::pos,
            ByteBufCodecs.BOOL,
            RequestServerChunk::generate,
            RequestServerChunk::new
    );
    
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public void handle(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(context.player().level() instanceof ServerLevel level))
            return;
        context.enqueueWork(() -> ChunkBatcher.submitRequest(new ChunkRequest(this.pos, X3dMap.MAP_GEOMATICS.get(), generate ? ChunkStatus.FULL : ChunkStatus.EMPTY, player, level) {
            @Override
            public void accept(ChunkPos p, ChunkResult<ChunkAccess> it) {
                it.ifSuccess(chunkAccess -> {
                    if (chunkAccess.getPersistedStatus().isOrAfter(ChunkStatus.FULL)) {
                        if (chunkAccess instanceof ImposterProtoChunk ipc) {
                            var pack = new SentChunkToClient(p, ipc.getWrapped());
                            CompletableFuture.runAsync(() -> PacketDistributor.sendToPlayer(player, pack));
                        }
                        if (chunkAccess instanceof LevelChunk levelChunk) {
                            var pack = new SentChunkToClient(p, levelChunk);
                            CompletableFuture.runAsync(() -> PacketDistributor.sendToPlayer(player, pack));
                        }
                    }
                });
            }
        }));
    }
}
