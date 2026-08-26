package com.xkball.x3dmap.block;

import com.mojang.serialization.MapCodec;
import com.xkball.x3dmap.X3dMap;
import com.xkball.x3dmap.block.entity.TerrainProjectorBlockEntity;
import com.xkball.x3dmap.network.s2c.OpenTerrainProjectorScreen;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

@NonNullByDefault
public final class TerrainProjectorBlock extends BaseEntityBlock {

    public static final MapCodec<TerrainProjectorBlock> CODEC = simpleCodec(TerrainProjectorBlock::new);

    public TerrainProjectorBlock(Block.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        this.openScreen(level, pos, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        this.openScreen(level, pos, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected int getLightDampening(BlockState state) {
        return 0;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TerrainProjectorBlockEntity(pos, state);
    }

    private void openScreen(Level level, BlockPos pos, Player player) {
        if (level.isClientSide() || !player.getAbilities().instabuild || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof TerrainProjectorBlockEntity) {
            PacketDistributor.sendToPlayer(serverPlayer, new OpenTerrainProjectorScreen(pos));
        }
    }
}
