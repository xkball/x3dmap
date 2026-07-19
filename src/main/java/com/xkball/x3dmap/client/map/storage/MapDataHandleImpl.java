package com.xkball.x3dmap.client.map.storage;

import com.xkball.x3dmap.api.client.storage.IMapDataHandle;
import com.xkball.x3dmap.api.client.storage.MapDataType;
import com.xkball.xklibmc.annotation.NonNullByDefault;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.UnaryOperator;

@NonNullByDefault
final class MapDataHandleImpl<T> implements IMapDataHandle<T> {

    private final MapDataType<T> type;
    private final Path file;
    private T value;
    private boolean dirty;

    MapDataHandleImpl(MapDataType<T> type, Path file, T value) {
        this.type = type;
        this.file = file;
        this.value = value;
    }

    MapDataType<T> type() {
        return this.type;
    }

    Path file() {
        return this.file;
    }

    @Override
    public T value() {
        return this.value;
    }

    @Override
    public void set(T value) {
        var newValue = Objects.requireNonNull(value);
        if (!Objects.equals(this.value, newValue)) {
            this.value = newValue;
            this.dirty = true;
        }
    }

    @Override
    public void update(UnaryOperator<T> updater) {
        this.set(updater.apply(this.value));
    }

    @Override
    public void markDirty() {
        this.dirty = true;
    }

    @Override
    public boolean dirty() {
        return this.dirty;
    }

    void clearDirty() {
        this.dirty = false;
    }
}
