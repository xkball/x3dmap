package com.xkball.x3dmap.client.map.storage;

import com.xkball.x3dmap.api.client.storage.ILevelDataAccess;
import com.xkball.x3dmap.api.client.storage.IMapDataHandle;
import com.xkball.x3dmap.api.client.storage.LevelDataType;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.nio.file.Path;

@NonNullByDefault
final class LevelDataAccessImpl extends MapDataAccess implements ILevelDataAccess {

    private final ResourceKey<Level> dimension;

    LevelDataAccessImpl(ResourceKey<Level> dimension, Path directory, MapStorageRegistry registry) {
        super(directory, registry.levelDataTypes());
        this.dimension = dimension;
    }

    @Override
    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    @Override
    public <T> IMapDataHandle<T> get(LevelDataType<T> type) {
        return this.getHandle(type);
    }
}
