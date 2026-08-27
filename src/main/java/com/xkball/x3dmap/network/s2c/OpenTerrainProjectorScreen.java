package com.xkball.x3dmap.network.s2c;

import com.xkball.x3dmap.block.entity.TerrainProjectorBlockEntity;
import com.xkball.x3dmap.ui.TerrainProjectorScreen;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@NonNullByDefault
public record OpenTerrainProjectorScreen(BlockPos pos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenTerrainProjectorScreen> TYPE = new CustomPacketPayload.Type<>(VanillaUtils.modRL("open_terrain_projector_screen"));
    public static final StreamCodec<ByteBuf, OpenTerrainProjectorScreen> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            OpenTerrainProjectorScreen::pos,
            OpenTerrainProjectorScreen::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            Handler.run(this.pos);
        });
    }
    
    public static class Handler {
        
        public static void run(BlockPos pos){
            var level = Minecraft.getInstance().level;
            if (level != null && level.getBlockEntity(pos) instanceof TerrainProjectorBlockEntity blockEntity) {
                Minecraft.getInstance().setScreen(new TerrainProjectorScreen(blockEntity));
            }
        }
    }
    
}
