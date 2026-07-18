package com.xkball.x3dmap.utils;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;

import java.util.Arrays;

@NonNullByDefault
public final class IntPalette {

    public static final StreamCodec<ByteBuf, IntPalette> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public IntPalette decode(ByteBuf input) {
            var paletteSize = VarInt.read(input);
            var palette = new IntPalette(paletteSize);
            for (int i = 0; i < paletteSize; i++) {
                palette.palette[i] = VarInt.read(input);
            }
            palette.paletteSize = paletteSize;

            var valueSize = VarInt.read(input);
            palette.ensureValueCapacity(valueSize);
            for (int i = 0; i < valueSize; i++) {
                palette.values[i] = VarInt.read(input);
            }
            palette.valueSize = valueSize;
            return palette;
        }

        @Override
        public void encode(ByteBuf output, IntPalette value) {
            VarInt.write(output, value.paletteSize);
            for (int i = 0; i < value.paletteSize; i++) {
                VarInt.write(output, value.palette[i]);
            }
            VarInt.write(output, value.valueSize);
            for (int i = 0; i < value.valueSize; i++) {
                VarInt.write(output, value.values[i]);
            }
        }
    };
    
    private int[] palette;
    private int paletteSize;
    private int[] values;
    private int valueSize;

    public IntPalette() {
        this(8);
    }

    public IntPalette(int initialCapacity) {
        this.palette = new int[initialCapacity];
        this.values = new int[initialCapacity];
    }

    public int idFor(int value) {
        for (int i = 0; i < this.paletteSize; i++) {
            if (this.palette[i] == value) {
                return i;
            }
        }
        this.ensurePaletteCapacity(this.paletteSize + 1);
        this.palette[this.paletteSize] = value;
        return this.paletteSize++;
    }

    public int valueFor(int id) {
        return this.palette[id];
    }

    public void add(int value) {
        this.ensureValueCapacity(this.valueSize + 1);
        this.values[this.valueSize++] = this.idFor(value);
    }

    public int get(int index) {
        return this.valueFor(this.values[index]);
    }

    public int size() {
        return this.valueSize;
    }

    public int getSize() {
        return this.paletteSize;
    }

    public int paletteSize() {
        return this.paletteSize;
    }

    public int[] toArray() {
        var result = new int[this.valueSize];
        for (int i = 0; i < this.valueSize; i++) {
            result[i] = this.palette[this.values[i]];
        }
        return result;
    }

    private void ensurePaletteCapacity(int requiredCapacity) {
        if (requiredCapacity > this.palette.length) {
            this.palette = Arrays.copyOf(this.palette, growCapacity(this.palette.length, requiredCapacity));
        }
    }

    private void ensureValueCapacity(int requiredCapacity) {
        if (requiredCapacity > this.values.length) {
            this.values = Arrays.copyOf(this.values, growCapacity(this.values.length, requiredCapacity));
        }
    }

    private static int growCapacity(int currentCapacity, int requiredCapacity) {
        var newCapacity = currentCapacity;
        while (newCapacity < requiredCapacity) {
            newCapacity = Math.max(requiredCapacity, newCapacity * 2);
        }
        return newCapacity;
    }
}
