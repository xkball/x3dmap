package com.xkball.x3dmap.api.client.storage;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

@NonNullByDefault
public interface ILevelDataAccess {

    ResourceKey<Level> dimension();

    <T> IMapDataHandle<T> get(LevelDataType<T> type);
}
