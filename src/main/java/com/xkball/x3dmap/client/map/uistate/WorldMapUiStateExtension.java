package com.xkball.x3dmap.client.map.uistate;

import com.xkball.x3dmap.api.client.map.WorldMapExtension;
import com.xkball.x3dmap.client.terrain.LevelChunkStorage;

public class WorldMapUiStateExtension implements WorldMapExtension {

    @Override
    public String id() {
        return WorldMapUiStateStorage.EXTENSION_ID;
    }

    @Override
    public void onStorageLoaded(LevelChunkStorage storage) {
        if (storage.getExtensionStorage(WorldMapUiStateStorage.EXTENSION_ID) == null) {
            storage.registerExtensionStorage(new WorldMapUiStateStorage());
        }
    }
}
