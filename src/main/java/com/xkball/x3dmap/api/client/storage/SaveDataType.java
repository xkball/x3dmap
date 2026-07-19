package com.xkball.x3dmap.api.client.storage;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

@NonNullByDefault
public final class SaveDataType<T> extends MapDataType<T> {

    private SaveDataType(Identifier id, StreamCodec<ByteBuf, T> codec, Supplier<T> factory) {
        super(id, codec, factory);
    }

    public static <T> SaveDataType<T> create(Identifier id, StreamCodec<ByteBuf, T> codec, Supplier<T> factory) {
        return new SaveDataType<>(id, codec, factory);
    }
}
