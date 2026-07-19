package com.xkball.x3dmap.client.map.plugin;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.api.client.IX3dMapPlugin;
import com.xkball.x3dmap.api.client.X3dMapPlugin;
import com.xkball.x3dmap.client.map.gui.MapGuiRegistry;
import com.xkball.x3dmap.client.map.gui.MapScreenSession;
import com.xkball.x3dmap.client.map.render.MapLayerRegistry;
import com.xkball.x3dmap.client.map.runtime.X3dMapRuntimeImpl;
import com.xkball.x3dmap.client.map.storage.MapStorageRegistry;
import com.xkball.x3dmap.client.map.storage.MapStorageManagerImpl;
import com.xkball.x3dmap.client.terrain.TerrainChunkManager;
import com.xkball.x3dmap.ui.widget.WorldTerrainWidget;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.neoforged.fml.ModList;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.annotation.ElementType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@NonNullByDefault
public final class X3dMapPluginRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<Identifier, IX3dMapPlugin> plugins = new LinkedHashMap<>();
    private final MapStorageRegistry storageRegistry = new MapStorageRegistry();
    private final MapGuiRegistry guiRegistry = new MapGuiRegistry();
    private final MapLayerRegistry layerRegistry = new MapLayerRegistry();
    private final List<MapScreenSession> sessions = new ArrayList<>();
    private @Nullable X3dMapRuntimeImpl runtime;
    private boolean initialized;
    private boolean runtimeAvailable;

    public void initialize(TerrainChunkManager terrainChunkManager) {
        if (this.initialized) {
            return;
        }
        this.initialized = true;
        this.runtime = new X3dMapRuntimeImpl(
                new MapStorageManagerImpl(this.storageRegistry),
                this.layerRegistry,
                terrainChunkManager
        );
        for (var plugin : this.discoverPlugins()) {
            this.registerPlugin(plugin);
        }
    }

    public void openRuntime(String saveName) {
        if (this.runtime == null) {
            return;
        }
        this.runtime.storageImpl().openSession(saveName);
        if (this.runtimeAvailable) {
            return;
        }
        this.runtimeAvailable = true;
        for (var plugin : this.plugins.values()) {
            try {
                plugin.onRuntimeAvailable(this.runtime);
            } catch (Exception e) {
                LOGGER.error("Failed to make map runtime available to plugin {}", plugin.getPluginUid(), e);
            }
        }
    }

    public void closeRuntime() {
        for (var session : List.copyOf(this.sessions)) {
            session.close();
        }
        this.sessions.clear();
        if (!this.runtimeAvailable || this.runtime == null) {
            return;
        }
        for (var plugin : this.plugins.values()) {
            try {
                plugin.onRuntimeUnavailable();
            } catch (Exception e) {
                LOGGER.error("Failed to make map runtime unavailable to plugin {}", plugin.getPluginUid(), e);
            }
        }
        this.runtime.storageImpl().closeSession();
        this.runtimeAvailable = false;
    }

    public void openLevel(ResourceKey<Level> dimension, Path directory) {
        if (this.runtime != null) {
            this.runtime.storageImpl().openLevel(dimension, directory);
        }
    }

    public void closeLevel(ResourceKey<Level> dimension) {
        if (this.runtime != null) {
            this.runtime.storageImpl().closeLevel(dimension);
        }
    }

    public void saveData() {
        if (this.runtime != null) {
            this.runtime.storageImpl().saveAll();
        }
    }

    public MapScreenSession openScreen(WorldTerrainWidget widget) {
        if (this.runtime == null) {
            throw new IllegalStateException("Map plugin registry is not initialized");
        }
        var session = new MapScreenSession(this.guiRegistry, this.runtime, widget);
        this.sessions.add(session);
        return session;
    }

    public void closeScreen(MapScreenSession session) {
        if (this.sessions.remove(session)) {
            session.close();
        }
    }

    public MapStorageRegistry storageRegistry() {
        return this.storageRegistry;
    }

    public MapLayerRegistry layerRegistry() {
        return this.layerRegistry;
    }

    public X3dMapRuntimeImpl runtime() {
        if (this.runtime == null) {
            throw new IllegalStateException("Map plugin registry is not initialized");
        }
        return this.runtime;
    }

    private void registerPlugin(IX3dMapPlugin plugin) {
        var id = plugin.getPluginUid();
        if (this.plugins.putIfAbsent(id, plugin) != null) {
            LOGGER.error("Duplicate map plugin registration: {}", id);
            return;
        }
        try {
            plugin.registerStorage(this.storageRegistry);
            plugin.registerLayers(this.layerRegistry);
            plugin.registerGui(this.guiRegistry);
        } catch (Exception e) {
            LOGGER.error("Failed to register map plugin {}", id, e);
            this.plugins.remove(id);
        }
    }

    private List<IX3dMapPlugin> discoverPlugins() {
        var plugins = new ArrayList<IX3dMapPlugin>();
        for (var scanData : ModList.get().getAllScanData()) {
            scanData.getAnnotatedBy(X3dMapPlugin.class, ElementType.TYPE).forEach(annotation -> {
                try {
                    var clazz = Class.forName(annotation.clazz().getClassName(), true, Thread.currentThread().getContextClassLoader());
                    if (!IX3dMapPlugin.class.isAssignableFrom(clazz)) {
                        LOGGER.error("Class {} is annotated as a map plugin but does not implement IX3dMapPlugin", clazz.getName());
                        return;
                    }
                    plugins.add((IX3dMapPlugin) clazz.getDeclaredConstructor().newInstance());
                } catch (Exception e) {
                    LOGGER.error("Failed to load map plugin {}", annotation.clazz().getClassName(), e);
                }
            });
        }
        plugins.sort(Comparator.comparing(plugin -> plugin.getPluginUid().toString()));
        return plugins;
    }
}
