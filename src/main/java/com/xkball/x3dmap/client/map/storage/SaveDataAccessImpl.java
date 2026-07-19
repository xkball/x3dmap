package com.xkball.x3dmap.client.map.storage;

import com.xkball.x3dmap.api.client.storage.IMapDataHandle;
import com.xkball.x3dmap.api.client.storage.ISaveDataAccess;
import com.xkball.x3dmap.api.client.storage.SaveDataType;
import com.xkball.xklibmc.annotation.NonNullByDefault;

import java.nio.file.Path;

@NonNullByDefault
final class SaveDataAccessImpl extends MapDataAccess implements ISaveDataAccess {

    SaveDataAccessImpl(Path directory, MapStorageRegistry registry) {
        super(directory, registry.saveDataTypes());
    }

    @Override
    public <T> IMapDataHandle<T> get(SaveDataType<T> type) {
        return this.getHandle(type);
    }
}
