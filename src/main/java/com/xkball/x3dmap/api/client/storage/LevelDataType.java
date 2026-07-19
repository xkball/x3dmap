package com.xkball.x3dmap.api.client.storage;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

@NonNullByDefault
public final class LevelDataType<T> extends MapDataType<T> {

    private LevelDataType(Identifier id, StreamCodec<ByteBuf, T> codec, Supplier<T> factory) {
        super(id, codec, factory);
    }

    public static <T> LevelDataType<T> create(Identifier id, StreamCodec<ByteBuf, T> codec, Supplier<T> factory) {
        return new LevelDataType<>(id, codec, factory);
    }
}
