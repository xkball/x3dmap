package com.xkball.x3dmap.api.client.registration;

import com.xkball.x3dmap.api.client.storage.LevelDataType;
import com.xkball.x3dmap.api.client.storage.SaveDataType;
import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public interface IMapStorageRegistration {

    <T> void registerSaveData(SaveDataType<T> type);

    <T> void registerLevelData(LevelDataType<T> type);
}
