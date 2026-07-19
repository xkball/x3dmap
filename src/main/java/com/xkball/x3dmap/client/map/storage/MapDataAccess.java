package com.xkball.x3dmap.client.map.storage;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.api.client.storage.IMapDataHandle;
import com.xkball.x3dmap.api.client.storage.MapDataType;
import com.xkball.x3dmap.client.map.uistate.WorldMapUiStateStorage;
import com.xkball.x3dmap.client.map.waypoint.WaypointStorage;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@NonNullByDefault
abstract class MapDataAccess {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<MapDataType<?>, MapDataHandleImpl<?>> handles = new LinkedHashMap<>();

    MapDataAccess(Path directory, Collection<? extends MapDataType<?>> types) {
        for (var type : types) {
            this.createHandle(directory, type);
        }
    }

    @SuppressWarnings("unchecked")
    protected <T> IMapDataHandle<T> getHandle(MapDataType<T> type) {
        var handle = this.handles.get(type);
        if (handle == null) {
            LOGGER.error("Map data type is not registered in this scope: {}", type.id());
            throw new IllegalArgumentException("Map data type is not registered in this scope: " + type.id());
        }
        return (IMapDataHandle<T>) handle;
    }

    public void save() {
        for (var handle : this.handles.values()) {
            this.saveHandle(handle);
        }
    }

    private <T> void createHandle(Path directory, MapDataType<T> type) {
        var file = directory.resolve(type.id().getPath());
        var value = this.load(type, file);
        var handle = new MapDataHandleImpl<>(type, file, value);
        if (value instanceof WaypointStorage waypointStorage) {
            waypointStorage.addDirtyListener(handle::markDirty);
        } else if (value instanceof WorldMapUiStateStorage uiStateStorage) {
            uiStateStorage.setDirtyListener(handle::markDirty);
        }
        this.handles.put(type, handle);
    }

    private <T> T load(MapDataType<T> type, Path file) {
        if (!Files.isRegularFile(file)) {
            return type.createDefault();
        }
        ByteBuf buffer = null;
        try {
            var bytes = VanillaUtils.unGzip(Files.readAllBytes(file));
            buffer = Unpooled.wrappedBuffer(bytes);
            return type.codec().decode(buffer);
        } catch (Exception e) {
            LOGGER.error("Failed to load map data {} from {}", type.id(), file.toAbsolutePath(), e);
            return type.createDefault();
        } finally {
            if (buffer != null) {
                buffer.release();
            }
        }
    }

    private <T> void saveHandle(MapDataHandleImpl<T> handle) {
        if (!handle.dirty()) {
            return;
        }
        ByteBuf buffer = null;
        try {
            buffer = Unpooled.buffer();
            handle.type().codec().encode(buffer, handle.value());
            var bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            var compressed = VanillaUtils.gzip(bytes, 0, bytes.length);
            var file = handle.file();
            Files.createDirectories(file.getParent());
            var temporaryFile = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temporaryFile, compressed);
            this.moveFile(temporaryFile, file);
            handle.clearDirty();
        } catch (Exception e) {
            LOGGER.error("Failed to save map data {} to {}", handle.type().id(), handle.file().toAbsolutePath(), e);
        } finally {
            if (buffer != null) {
                buffer.release();
            }
        }
    }

    private void moveFile(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
