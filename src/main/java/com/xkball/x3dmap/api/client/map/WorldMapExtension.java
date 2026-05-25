package com.xkball.x3dmap.api.client.map;

import com.xkball.x3dmap.client.terrain.LevelChunkStorage;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface WorldMapExtension {
    
    String id();
    
    default int order() {
        return 0;
    }
    
    default void init(WorldMapExtensionContext context) {
    }
    
    default void onStorageLoaded(LevelChunkStorage storage) {
    }
    
    default void onStorageSaving(LevelChunkStorage storage) {
    }
    
    default void onStorageClosed(@Nullable LevelChunkStorage storage) {
    }
    
    default void onMapOpened(WorldMapExtensionService service) {
    }
    
    default void onMapClosed(WorldMapExtensionService service) {
    }
    
    default void onMapEvent(WorldMapExtensionService service, WorldMapEvent event) {
    }
    
    default void tick(WorldMapExtensionService service) {
    }
    
    default List<String> enabledLayers(WorldMapExtensionService service) {
        return List.of();
    }
}
