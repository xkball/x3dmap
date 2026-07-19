package com.xkball.x3dmap.api.client.storage;

import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public interface ISaveDataAccess {

    <T> IMapDataHandle<T> get(SaveDataType<T> type);
}
