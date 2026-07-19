package com.xkball.x3dmap.client.map.storage;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.api.client.registration.IMapStorageRegistration;
import com.xkball.x3dmap.api.client.storage.LevelDataType;
import com.xkball.x3dmap.api.client.storage.MapDataType;
import com.xkball.x3dmap.api.client.storage.SaveDataType;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@NonNullByDefault
public final class MapStorageRegistry implements IMapStorageRegistration {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<Identifier, SaveDataType<?>> saveDataTypes = new LinkedHashMap<>();
    private final Map<Identifier, LevelDataType<?>> levelDataTypes = new LinkedHashMap<>();

    @Override
    public <T> void registerSaveData(SaveDataType<T> type) {
        this.register(this.saveDataTypes, type);
    }

    @Override
    public <T> void registerLevelData(LevelDataType<T> type) {
        this.register(this.levelDataTypes, type);
    }

    public Collection<SaveDataType<?>> saveDataTypes() {
        return this.saveDataTypes.values();
    }

    public Collection<LevelDataType<?>> levelDataTypes() {
        return this.levelDataTypes.values();
    }

    private <T extends MapDataType<?>> void register(Map<Identifier, T> types, T type) {
        var old = types.putIfAbsent(type.id(), type);
        if (old != null) {
            LOGGER.error("Duplicate map data type registration: {}", type.id());
            throw new IllegalArgumentException("Duplicate map data type registration: " + type.id());
        }
    }
}
