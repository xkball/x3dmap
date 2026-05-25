package com.xkball.x3dmap.api.client.map;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.client.terrain.LevelChunkStorage;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

public class WorldMapExtensionRegistry {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private final List<WorldMapExtension> extensions = new ArrayList<>();
    private final List<WorldMapExtensionService> openedServices = new ArrayList<>();
    private boolean initialized;
    
    public void init(WorldMapExtensionContext context) {
        if (this.initialized) {
            return;
        }
        this.initialized = true;
        for (var extension : ServiceLoader.load(WorldMapExtension.class)) {
            try {
                extension.init(context);
                this.extensions.add(extension);
            } catch (Exception e) {
                LOGGER.error("Failed to initialize world map extension {}", extension.id(), e);
            }
        }
        this.extensions.sort(Comparator.comparingInt(WorldMapExtension::order));
    }
    
    public List<WorldMapExtension> extensions() {
        return Collections.unmodifiableList(this.extensions);
    }
    
    public void onStorageLoaded(LevelChunkStorage storage) {
        for (var extension : this.extensions) {
            try {
                extension.onStorageLoaded(storage);
            } catch (Exception e) {
                LOGGER.error("Failed to notify world map extension {} about storage load", extension.id(), e);
            }
        }
    }
    
    public void onStorageSaving(LevelChunkStorage storage) {
        for (var extension : this.extensions) {
            try {
                extension.onStorageSaving(storage);
            } catch (Exception e) {
                LOGGER.error("Failed to notify world map extension {} about storage save", extension.id(), e);
            }
        }
    }
    
    public void onStorageClosed(@Nullable LevelChunkStorage storage) {
        for (var extension : this.extensions) {
            try {
                extension.onStorageClosed(storage);
            } catch (Exception e) {
                LOGGER.error("Failed to notify world map extension {} about storage close", extension.id(), e);
            }
        }
    }
    
    public void onMapOpened(WorldMapExtensionService service) {
        this.openedServices.add(service);
        for (var extension : this.extensions) {
            try {
                extension.onMapOpened(service.scope(extension.id()));
            } catch (Exception e) {
                LOGGER.error("Failed to open world map extension {}", extension.id(), e);
            }
        }
    }
    
    public void onMapClosed(WorldMapExtensionService service) {
        this.openedServices.remove(service);
        for (var extension : this.extensions) {
            try {
                extension.onMapClosed(service.scope(extension.id()));
            } catch (Exception e) {
                LOGGER.error("Failed to close world map extension {}", extension.id(), e);
            }
        }
    }
    
    public void onMapEvent(WorldMapExtensionService service, WorldMapEvent event) {
        for (var extension : this.extensions) {
            try {
                extension.onMapEvent(service.scope(extension.id()), event);
            } catch (Exception e) {
                LOGGER.error("Failed to dispatch world map event to extension {}", extension.id(), e);
            }
            if (event instanceof WorldMapEvent.Input input && input.consumed()) {
                return;
            }
        }
    }
    
    public void tick() {
        for (var service : List.copyOf(this.openedServices)) {
            this.tick(service);
        }
    }
    
    public void tick(WorldMapExtensionService service) {
        for (var extension : this.extensions) {
            try {
                extension.tick(service.scope(extension.id()));
            } catch (Exception e) {
                LOGGER.error("Failed to tick world map extension {}", extension.id(), e);
            }
        }
    }
    
    public List<String> enabledLayers(WorldMapExtensionService service) {
        var layers = new ArrayList<String>();
        for (var extension : this.extensions) {
            try {
                layers.addAll(extension.enabledLayers(service.scope(extension.id())));
            } catch (Exception e) {
                LOGGER.error("Failed to collect enabled layers from world map extension {}", extension.id(), e);
            }
        }
        return layers;
    }
}
