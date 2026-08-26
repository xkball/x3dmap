package com.xkball.x3dmap.block.entity;

import com.xkball.x3dmap.block.X3dMapBlocks;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

@NonNullByDefault
public final class TerrainProjectorBlockEntity extends BlockEntity implements IClientUpdateBlockEntity {

    public BlockPos centerPos;
    public int projectionRadius = 1;
    public int lodLevel;

    public TerrainProjectorBlockEntity(BlockPos pos, BlockState blockState) {
        super(X3dMapBlocks.TERRAIN_PROJECTOR_BLOCK_ENTITY.get(), pos, blockState);
        this.centerPos = pos;
    }

    public void setParameters(BlockPos centerPos, int projectionRadius, int lodLevel) {
        this.centerPos = centerPos;
        this.projectionRadius = Math.clamp(projectionRadius, 1, 30000000);
        this.lodLevel = Math.clamp(lodLevel, 0, 4);
        this.setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.read(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.write(output);
    }

    @Override
    public void updateFromClient(ValueInput input) {
        this.read(input);
    }

    @Override
    public void writeFromClient(ValueOutput output) {
        this.write(output);
    }

    public void read(ValueInput input) {
        this.centerPos = input.read("center", BlockPos.CODEC).orElse(this.worldPosition);
        this.projectionRadius = Math.clamp(input.getIntOr("radius", 1), 1, 30000000);
        this.lodLevel = Math.clamp(input.getIntOr("lod", 0), 0, 4);
    }

    public void write(ValueOutput output) {
        output.store("center", BlockPos.CODEC, this.centerPos);
        output.putInt("radius", this.projectionRadius);
        output.putInt("lod", this.lodLevel);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
