package com.xkball.x3dmap.network.c2s;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.block.entity.IClientUpdateBlockEntity;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

@NonNullByDefault
public record UpdateBlockEntityData(CompoundTag data, BlockPos location) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ProblemReporter.PathElement PATH = new ClientPathElement();
    public static final Type<UpdateBlockEntityData> TYPE = new Type<>(VanillaUtils.modRL("update_block_entity_data"));
    public static final StreamCodec<ByteBuf, UpdateBlockEntityData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG,
            UpdateBlockEntityData::data,
            BlockPos.STREAM_CODEC,
            UpdateBlockEntityData::location,
            UpdateBlockEntityData::new
    );

    public static UpdateBlockEntityData create(BlockEntity entity) {
        var tag = new CompoundTag();
        if (entity instanceof IClientUpdateBlockEntity clientUpdateBlockEntity) {
            var registries = entity.getLevel().registryAccess();
            try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(PATH, LOGGER)) {
                TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
                clientUpdateBlockEntity.writeFromClient(output);
                tag = output.buildResult();
            }
        }
        return new UpdateBlockEntityData(tag, entity.getBlockPos());
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var sender = context.player();
            var level = sender.level();
            if (!level.isLoaded(this.location)) {
                return;
            }
            var blockEntity = level.getBlockEntity(this.location);
            if (blockEntity instanceof IClientUpdateBlockEntity clientUpdateBlockEntity) {
                var registries = blockEntity.getLevel().registryAccess();
                try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(PATH, LOGGER)) {
                    var input = TagValueInput.create(reporter, registries, this.data);
                    clientUpdateBlockEntity.updateFromClient(input);
                }
                var state = level.getBlockState(this.location);
                blockEntity.setChanged();
                level.sendBlockUpdated(this.location, state, state, Block.UPDATE_CLIENTS);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static final class ClientPathElement implements ProblemReporter.PathElement {

        @Override
        public String get() {
            return "x3d_map:client_update";
        }
    }
}
