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

import java.util.ArrayList;
import java.util.List;

@NonNullByDefault
public final class TerrainProjectorBlockEntity extends BlockEntity implements IClientUpdateBlockEntity {

    public BlockPos centerPos;
    public int projectionRadius = 1;
    public int lodLevel;
    public float yOffset;
    private BlockPos normalizedCenterPos = BlockPos.ZERO;
    private List<BlockPos> modelPositions = List.of();

    public TerrainProjectorBlockEntity(BlockPos pos, BlockState blockState) {
        super(X3dMapBlocks.TERRAIN_PROJECTOR_BLOCK_ENTITY.get(), pos, blockState);
        this.centerPos = pos.atY(0);
    }

    public void setParameters(BlockPos centerPos, int projectionRadius, int lodLevel, float yOffset) {
        this.centerPos = centerPos.atY(0);
        this.projectionRadius = Math.clamp(projectionRadius, 0, 32);
        this.lodLevel = Math.clamp(lodLevel, 0, 4);
        this.yOffset = Math.clamp(yOffset, -2048.0F, 2048.0F);
        this.rebuildClientData();
        this.setChanged();
    }

    public BlockPos getNormalizedCenterPos() {
        return this.normalizedCenterPos;
    }

    public List<BlockPos> getModelPositions() {
        return this.modelPositions;
    }

    public int getModelSideLength() {
        return 1 << (this.lodLevel + 5);
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
        this.centerPos = input.read("center", BlockPos.CODEC).orElse(this.worldPosition).atY(0);
        this.projectionRadius = Math.clamp(input.getIntOr("radius", 1), 0, 32);
        this.lodLevel = Math.clamp(input.getIntOr("lod", 0), 0, 4);
        this.yOffset = Math.clamp(input.getFloatOr("y_offset", 0), -2048.0F, 2048.0F);
        this.rebuildClientData();
    }

    public void write(ValueOutput output) {
        output.store("center", BlockPos.CODEC, this.centerPos);
        output.putInt("radius", this.projectionRadius);
        output.putInt("lod", this.lodLevel);
        output.putFloat("y_offset", this.yOffset);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.rebuildClientData();
    }

    private void rebuildClientData() {
        if (this.level == null || !this.level.isClientSide()) {
            return;
        }
        var sideLength = this.getModelSideLength();
        var normalizedX = Math.floorDiv(this.centerPos.getX(), sideLength) * sideLength;
        var normalizedZ = Math.floorDiv(this.centerPos.getZ(), sideLength) * sideLength;
        this.normalizedCenterPos = new BlockPos(normalizedX, 0, normalizedZ);
        var positions = new ArrayList<BlockPos>();
        for (var dx = -this.projectionRadius; dx <= this.projectionRadius; dx++) {
            for (var dz = -this.projectionRadius; dz <= this.projectionRadius; dz++) {
                positions.add(this.normalizedCenterPos.offset(dx * sideLength, 0, dz * sideLength));
            }
        }
        this.modelPositions = List.copyOf(positions);
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
