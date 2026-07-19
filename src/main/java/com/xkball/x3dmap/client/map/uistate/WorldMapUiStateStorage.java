package com.xkball.x3dmap.client.map.uistate;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@NonNullByDefault
public class WorldMapUiStateStorage {
    
    private static final StreamCodec<ByteBuf, Map<String, Boolean>> BOOLEAN_MAP_CODEC = ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.BOOL);
    private static final StreamCodec<ByteBuf, Map<String, Integer>> INT_MAP_CODEC = ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.INT);
    private static final StreamCodec<ByteBuf, Map<String, Float>> FLOAT_MAP_CODEC = ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.FLOAT);
    private static final StreamCodec<ByteBuf, Map<String, String>> STRING_MAP_CODEC = ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8);
    public static final StreamCodec<ByteBuf, WorldMapUiStateStorage> STREAM_CODEC = StreamCodec.composite(
            BOOLEAN_MAP_CODEC,
            storage -> storage.booleanValues,
            INT_MAP_CODEC,
            storage -> storage.intValues,
            FLOAT_MAP_CODEC,
            storage -> storage.floatValues,
            STRING_MAP_CODEC,
            storage -> storage.stringValues,
            WorldMapUiStateStorage::new
    );

    private final Map<String, Boolean> booleanValues = new HashMap<>();
    private final Map<String, Integer> intValues = new HashMap<>();
    private final Map<String, Float> floatValues = new HashMap<>();
    private final Map<String, String> stringValues = new HashMap<>();
    private @Nullable Runnable dirtyListener;

    public WorldMapUiStateStorage() {
    }

    private WorldMapUiStateStorage(Map<String, Boolean> booleanValues, Map<String, Integer> intValues, Map<String, Float> floatValues, Map<String, String> stringValues) {
        this.booleanValues.putAll(booleanValues);
        this.intValues.putAll(intValues);
        this.floatValues.putAll(floatValues);
        this.stringValues.putAll(stringValues);
    }

    public void setDirtyListener(@Nullable Runnable dirtyListener) {
        this.dirtyListener = dirtyListener;
    }
    
    public boolean contains(String key) {
        return this.booleanValues.containsKey(key)
                || this.intValues.containsKey(key)
                || this.floatValues.containsKey(key)
                || this.stringValues.containsKey(key);
    }
    
    public void remove(String key) {
        var changed = this.booleanValues.remove(key) != null;
        changed |= this.intValues.remove(key) != null;
        changed |= this.floatValues.remove(key) != null;
        changed |= this.stringValues.remove(key) != null;
        if (changed) {
            this.markDirty();
        }
    }
    
    public boolean getBoolean(String key, boolean defaultValue) {
        return this.booleanValues.getOrDefault(key, defaultValue);
    }
    
    public void setBoolean(String key, boolean value) {
        this.setValue(key, value, this.booleanValues);
    }
    
    public int getInt(String key, int defaultValue) {
        return this.intValues.getOrDefault(key, defaultValue);
    }
    
    public void setInt(String key, int value) {
        this.setValue(key, value, this.intValues);
    }
    
    public float getFloat(String key, float defaultValue) {
        return this.floatValues.getOrDefault(key, defaultValue);
    }
    
    public void setFloat(String key, float value) {
        this.setValue(key, value, this.floatValues);
    }
    
    public String getString(String key, String defaultValue) {
        return this.stringValues.getOrDefault(key, defaultValue);
    }
    
    public void setString(String key, @Nullable String value) {
        if (value == null) {
            this.remove(key);
            return;
        }
        this.setValue(key, value, this.stringValues);
    }
    
    private <T> void setValue(String key, T value, Map<String, T> target) {
        var changed = this.removeOtherValues(key, target);
        var oldValue = target.put(key, value);
        if (changed || !Objects.equals(oldValue, value)) {
            this.markDirty();
        }
    }

    private boolean removeOtherValues(String key, Map<String, ?> target) {
        var changed = target != this.booleanValues && this.booleanValues.remove(key) != null;
        changed |= target != this.intValues && this.intValues.remove(key) != null;
        changed |= target != this.floatValues && this.floatValues.remove(key) != null;
        changed |= target != this.stringValues && this.stringValues.remove(key) != null;
        return changed;
    }
    
    private void markDirty() {
        if (this.dirtyListener != null) {
            this.dirtyListener.run();
        }
    }
}
