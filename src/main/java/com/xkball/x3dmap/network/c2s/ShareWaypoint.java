package com.xkball.x3dmap.network.c2s;

import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@NonNullByDefault
public record ShareWaypoint(String name, BlockPos pos) implements CustomPacketPayload {

    public static final int MAX_NAME_LENGTH = 64;
    public static final CustomPacketPayload.Type<ShareWaypoint> TYPE = new Type<>(VanillaUtils.modRL("share_waypoint"));
    public static final StreamCodec<ByteBuf, ShareWaypoint> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH),
            ShareWaypoint::name,
            BlockPos.STREAM_CODEC,
            ShareWaypoint::pos,
            ShareWaypoint::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        context.enqueueWork(() -> {
            var name = this.name.replaceAll("\\p{Cntrl}", " ").strip();
            var x = this.pos.getX();
            var y = this.pos.getY();
            var z = this.pos.getZ();
            var command = "/x3dmap waypoint add " + x + " " + y + " " + z + " " + name;
            var playerName = Component.literal(player.getGameProfile().name())
                    .withStyle(style -> style.withColor(ChatFormatting.AQUA));
            var label = Component.translatable("xklibmc.waypoint.share.label", name, x, y, z)
                    .withStyle(style -> style
                            .withColor(ChatFormatting.YELLOW)
                            .withClickEvent(new ClickEvent.RunCommand(command))
                            .withHoverEvent(new HoverEvent.ShowText(Component.translatable("xklibmc.waypoint.share.hover"))));
            var message = Component.translatable("xklibmc.waypoint.share.message", playerName, label);
            level.getServer().getPlayerList().broadcastSystemMessage(message, false);
        });
    }
}
