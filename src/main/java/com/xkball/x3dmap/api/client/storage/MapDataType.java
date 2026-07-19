package com.xkball.x3dmap.api.client.storage;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.function.Supplier;

@NonNullByDefault
public abstract class MapDataType<T> {

    private final Identifier id;
    private final StreamCodec<ByteBuf, T> codec;
    private final Supplier<T> factory;

    protected MapDataType(Identifier id, StreamCodec<ByteBuf, T> codec, Supplier<T> factory) {
        this.id = Objects.requireNonNull(id);
        this.codec = Objects.requireNonNull(codec);
        this.factory = Objects.requireNonNull(factory);
    }

    public Identifier id() {
        return this.id;
    }

    public StreamCodec<ByteBuf, T> codec() {
        return this.codec;
    }

    public T createDefault() {
        return this.factory.get();
    }
}
