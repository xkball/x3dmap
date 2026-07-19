package com.xkball.x3dmap.api.client.storage;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;

@NonNullByDefault
public interface IMapStorageManager {

    ISaveDataAccess saveData();

    Optional<ILevelDataAccess> levelData(ResourceKey<Level> dimension);

    Optional<ILevelDataAccess> currentLevelData();
}
