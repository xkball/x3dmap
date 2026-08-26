package com.xkball.x3dmap.block.entity;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

@NonNullByDefault
public interface IClientUpdateBlockEntity {

    void updateFromClient(ValueInput input);

    void writeFromClient(ValueOutput output);
}
