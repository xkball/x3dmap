package com.xkball.x3dmap.client.map.uistate;

import com.xkball.x3dmap.api.client.map.WorldMapExtensionStorage;
import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class WorldMapUiStateStorage implements WorldMapExtensionStorage {

    public static final String EXTENSION_ID = "ui_state";
    private static final int VERSION = 1;
    private static final byte TYPE_BOOLEAN = 1;
    private static final byte TYPE_INT = 2;
    private static final byte TYPE_FLOAT = 3;
    private static final byte TYPE_STRING = 4;

    private final Map<String, Object> values = new HashMap<>();
    private boolean dirty;

    @Override
    public String extensionId() {
        return EXTENSION_ID;
    }

    @Override
    public boolean dirty() {
        return this.dirty;
    }

    @Override
    public void clearDirty() {
        this.dirty = false;
    }

    public boolean contains(String key) {
        return this.values.containsKey(key);
    }

    public void remove(String key) {
        if (this.values.remove(key) != null) {
            this.markDirty();
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return this.values.get(key) instanceof Boolean value ? value : defaultValue;
    }

    public void setBoolean(String key, boolean value) {
        this.setValue(key, value);
    }

    public int getInt(String key, int defaultValue) {
        return this.values.get(key) instanceof Integer value ? value : defaultValue;
    }

    public void setInt(String key, int value) {
        this.setValue(key, value);
    }

    public float getFloat(String key, float defaultValue) {
        return this.values.get(key) instanceof Float value ? value : defaultValue;
    }

    public void setFloat(String key, float value) {
        this.setValue(key, value);
    }

    public String getString(String key, String defaultValue) {
        return this.values.get(key) instanceof String value ? value : defaultValue;
    }

    public void setString(String key, @Nullable String value) {
        if (value == null) {
            this.remove(key);
            return;
        }
        this.setValue(key, value);
    }

    @Override
    public void load(ByteBuf byteBuf) {
        this.values.clear();
        var version = byteBuf.readInt();
        if (version != VERSION) {
            return;
        }
        var size = byteBuf.readInt();
        for (var i = 0; i < size; i++) {
            var key = this.readString(byteBuf);
            var type = byteBuf.readByte();
            switch (type) {
                case TYPE_BOOLEAN -> this.values.put(key, byteBuf.readBoolean());
                case TYPE_INT -> this.values.put(key, byteBuf.readInt());
                case TYPE_FLOAT -> this.values.put(key, byteBuf.readFloat());
                case TYPE_STRING -> this.values.put(key, this.readString(byteBuf));
                default -> {
                }
            }
        }
        this.clearDirty();
    }

    @Override
    public void save(ByteBuf byteBuf) {
        byteBuf.writeInt(VERSION);
        byteBuf.writeInt(this.values.size());
        for (var entry : this.values.entrySet()) {
            this.writeString(byteBuf, entry.getKey());
            var value = entry.getValue();
            if (value instanceof Boolean boolValue) {
                byteBuf.writeByte(TYPE_BOOLEAN);
                byteBuf.writeBoolean(boolValue);
            } else if (value instanceof Integer intValue) {
                byteBuf.writeByte(TYPE_INT);
                byteBuf.writeInt(intValue);
            } else if (value instanceof Float floatValue) {
                byteBuf.writeByte(TYPE_FLOAT);
                byteBuf.writeFloat(floatValue);
            } else {
                byteBuf.writeByte(TYPE_STRING);
                this.writeString(byteBuf, Objects.toString(value, ""));
            }
        }
    }

    private void setValue(String key, Object value) {
        var oldValue = this.values.put(key, value);
        if (!Objects.equals(oldValue, value)) {
            this.markDirty();
        }
    }

    private void markDirty() {
        this.dirty = true;
    }

    private void writeString(ByteBuf byteBuf, String value) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        byteBuf.writeInt(bytes.length);
        byteBuf.writeBytes(bytes);
    }

    private String readString(ByteBuf byteBuf) {
        var length = byteBuf.readInt();
        var bytes = new byte[length];
        byteBuf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
