package com.xkball.x3dmap.api.client.storage;

import com.xkball.xklibmc.annotation.NonNullByDefault;

import java.util.function.UnaryOperator;

@NonNullByDefault
public interface IMapDataHandle<T> {

    T value();

    void set(T value);

    void update(UnaryOperator<T> updater);

    void markDirty();

    boolean dirty();
}
