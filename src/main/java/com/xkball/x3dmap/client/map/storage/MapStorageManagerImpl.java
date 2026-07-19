package com.xkball.x3dmap.client.map.storage;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.api.client.storage.ILevelDataAccess;
import com.xkball.x3dmap.api.client.storage.IMapStorageManager;
import com.xkball.x3dmap.api.client.storage.ISaveDataAccess;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@NonNullByDefault
public final class MapStorageManagerImpl implements IMapStorageManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final MapStorageRegistry registry;
    private final Map<ResourceKey<Level>, LevelDataAccessImpl> levelData = new LinkedHashMap<>();
    private @Nullable SaveDataAccessImpl saveData;
    private @Nullable String saveName;

    public MapStorageManagerImpl(MapStorageRegistry registry) {
        this.registry = registry;
    }

    public void openSession(String saveName) {
        if (saveName.equals(this.saveName) && this.saveData != null) {
            return;
        }
        this.closeSession();
        this.saveName = saveName;
        this.saveData = new SaveDataAccessImpl(this.sessionDirectory().resolve("_save"), this.registry);
    }

    public void openLevel(ResourceKey<Level> dimension, Path directory) {
        this.levelData.computeIfAbsent(dimension, key -> new LevelDataAccessImpl(key, directory, this.registry));
    }

    public void closeLevel(ResourceKey<Level> dimension) {
        var access = this.levelData.remove(dimension);
        if (access != null) {
            access.save();
        }
    }

    public void saveAll() {
        if (this.saveData != null) {
            this.saveData.save();
        }
        for (var access : this.levelData.values()) {
            access.save();
        }
    }

    public void closeSession() {
        this.saveAll();
        this.levelData.clear();
        this.saveData = null;
        this.saveName = null;
    }

    @Override
    public ISaveDataAccess saveData() {
        if (this.saveData == null) {
            LOGGER.error("Map save data was requested without an active session");
            throw new IllegalStateException("Map save data was requested without an active session");
        }
        return this.saveData;
    }

    @Override
    public Optional<ILevelDataAccess> levelData(ResourceKey<Level> dimension) {
        return Optional.ofNullable(this.levelData.get(dimension));
    }

    @Override
    public Optional<ILevelDataAccess> currentLevelData() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return Optional.empty();
        }
        return this.levelData(level.dimension());
    }

    private Path sessionDirectory() {
        return FMLPaths.GAMEDIR.get().resolve("x3dmap").resolve(this.saveName);
    }
}
