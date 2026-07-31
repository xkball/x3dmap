package com.xkball.x3dmap.client.map.gui;

import com.xkball.x3dmap.api.client.gui.IMapGui;
import com.xkball.x3dmap.api.client.gui.IMapScreenContext;
import com.xkball.x3dmap.api.client.runtime.IX3dMapRuntime;
import com.xkball.x3dmap.api.client.viewport.IMapViewport;
import com.xkball.x3dmap.client.map.storage.BuiltinMapDataTypes;
import com.xkball.x3dmap.client.map.uistate.WorldMapUiStateStorage;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

@NonNullByDefault
public record MapScreenContextImpl(
        Identifier extensionId,
        IX3dMapRuntime runtime,
        IMapViewport viewport,
        IMapGui gui
) implements IMapScreenContext {

    @Override
    public boolean containsState(String key) {
        var storage = this.uiStateStorage();
        return storage != null && storage.contains(this.stateKey(key));
    }

    @Override
    public void removeState(String key) {
        var storage = this.uiStateStorage();
        if (storage != null) {
            storage.remove(this.stateKey(key));
        }
    }

    @Override
    public boolean getBooleanState(String key, boolean defaultValue) {
        var storage = this.uiStateStorage();
        return storage == null ? defaultValue : storage.getBoolean(this.stateKey(key), defaultValue);
    }

    @Override
    public void setBooleanState(String key, boolean value) {
        var storage = this.uiStateStorage();
        if (storage != null) {
            storage.setBoolean(this.stateKey(key), value);
        }
    }

    @Override
    public int getIntState(String key, int defaultValue) {
        var storage = this.uiStateStorage();
        return storage == null ? defaultValue : storage.getInt(this.stateKey(key), defaultValue);
    }

    @Override
    public void setIntState(String key, int value) {
        var storage = this.uiStateStorage();
        if (storage != null) {
            storage.setInt(this.stateKey(key), value);
        }
    }

    @Override
    public float getFloatState(String key, float defaultValue) {
        var storage = this.uiStateStorage();
        return storage == null ? defaultValue : storage.getFloat(this.stateKey(key), defaultValue);
    }

    @Override
    public void setFloatState(String key, float value) {
        var storage = this.uiStateStorage();
        if (storage != null) {
            storage.setFloat(this.stateKey(key), value);
        }
    }

    @Override
    public String getStringState(String key, String defaultValue) {
        var storage = this.uiStateStorage();
        return storage == null ? defaultValue : storage.getString(this.stateKey(key), defaultValue);
    }

    @Override
    public void setStringState(String key, String value) {
        var storage = this.uiStateStorage();
        if (storage != null) {
            storage.setString(this.stateKey(key), value);
        }
    }

    private @Nullable WorldMapUiStateStorage uiStateStorage() {
        return this.runtime.storage().currentLevelData()
                .map(access -> access.get(BuiltinMapDataTypes.UI_STATE).value())
                .orElse(null);
    }

    private String stateKey(String key) {
        return this.extensionId + ":" + key;
    }
}
