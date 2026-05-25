package com.xkball.x3dmap.client.map;

import com.xkball.xklib.ui.css.property.value.CssLengthUnit;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.system.GuiSystem;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklib.ui.widget.container.WindowedContainer;
import com.xkball.x3dmap.api.client.map.WorldMapExtensionService;
import com.xkball.x3dmap.client.terrain.LevelChunkStorage;
import com.xkball.x3dmap.client.terrain.TerrainChunkManager;
import com.xkball.x3dmap.client.map.uistate.WorldMapUiStateStorage;
import com.xkball.x3dmap.ui.widget.WorldTerrainWidget;
import com.xkball.x3dmap.ui.widget.WorldTerrainWidgetInner;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public class WorldMapExtensionServiceImpl implements WorldMapExtensionService {

    public WorldTerrainWidget widget;
    public final String extensionId;
    
    public WorldMapExtensionServiceImpl(String extensionId) {
        this.extensionId = extensionId;
    }

    public WorldMapExtensionServiceImpl(WorldTerrainWidget widget, String extensionId) {
        this.widget = widget;
        this.extensionId = extensionId;
    }

    @Override
    public String extensionId() {
        return this.extensionId;
    }

    @Override
    public WorldMapExtensionService scope(String extensionId) {
        return new WorldMapExtensionServiceImpl(this.widget, extensionId);
    }

    @Override
    public void addLeftBarWidget(Widget widget) {
        if(this.widget == null) return;
        this.widget.addExtensionLeftBarWidget(widget);
    }

    @Override
    public void addTopBar1Widget(Widget widget) {
        if(this.widget == null) return;
        this.widget.addExtensionTopBar1Widget(widget);
    }

    @Override
    public void addTopBar2Widget(Widget widget) {
        if(this.widget == null) return;
        this.widget.addExtensionTopBar2Widget(widget);
    }

    @Override
    public WindowedContainer.SubWindow addSubWindow(Widget content, CssLengthUnit width, CssLengthUnit height) {
        return this.widget.addMapSubWindow(content, width, height);
    }

    @Override
    public WindowedContainer.SubWindow addSubWindow(Widget content, float x, float y, CssLengthUnit width, CssLengthUnit height) {
        return this.widget.addMapSubWindow(content, x, y, width, height);
    }

    @Override
    public WindowedContainer.SubWindow addSubWindow(Widget content, IComponent title, boolean resizable, CssLengthUnit width, CssLengthUnit height) {
        return this.widget.addMapSubWindow(content, title, resizable, width, height);
    }

    @Override
    public WindowedContainer.SubWindow addSubWindow(Widget content, String title, boolean resizable, CssLengthUnit width, CssLengthUnit height) {
        return this.widget.addMapSubWindow(content, title, resizable, width, height);
    }

    @Override
    public WindowedContainer.SubWindow addSubWindow(Widget content, IComponent title, boolean resizable, float x, float y, CssLengthUnit width, CssLengthUnit height) {
        return this.widget.addMapSubWindow(content, title, resizable, x, y, width, height);
    }

    @Override
    public WindowedContainer.SubWindow addSubWindow(Widget content, String title, boolean resizable, float x, float y, CssLengthUnit width, CssLengthUnit height) {
        return this.widget.addMapSubWindow(content, title, resizable, x, y, width, height);
    }

    @Override
    public WindowedContainer.SubWindow addBlockingSubWindow(Widget content, IComponent title, boolean resizable, CssLengthUnit width, CssLengthUnit height) {
        var parent = this.widget.windowLayer();
        var parentWidth = parent.getWidth();
        var parentHeight = parent.getHeight();
        var w = width.resolve(parentWidth);
        var h = height.resolve(parentHeight);
        var x = Math.max(0f, (parentWidth - w) / 2f);
        var y = Math.max(0f, (parentHeight - h) / 2f);
        return this.addBlockingSubWindow(content, title, resizable, x, y, width, height);
    }

    @Override
    public WindowedContainer.SubWindow addBlockingSubWindow(Widget content, String title, boolean resizable, CssLengthUnit width, CssLengthUnit height) {
        return addBlockingSubWindow(content, IComponent.literal(title), resizable, width, height);
    }

    @Override
    public WindowedContainer.SubWindow addBlockingSubWindow(Widget content, IComponent title, boolean resizable, float x, float y, CssLengthUnit width, CssLengthUnit height, boolean autoShrinkHeight) {
        var layer = new WindowedContainer();
        layer.setAutoRemoveFromGuiSystemWhenEmpty(true);
        layer.inlineStyle("size: 100% 100%; background-color: 0x55000000;");
        layer.setBlockInput(true);
        var parent = this.widget.windowLayer();
        var w = width.resolve(parent.getWidth());
        var h = height.resolve(parent.getHeight());
        var window = layer.addSubWindow(content, title, resizable, x, y, w, h);
        window.setAutoHeight(autoShrinkHeight);
        GuiSystem.INSTANCE.get().insertLayerAfter(layer, parent);
        return window;
    }

    @Override
    public WindowedContainer.SubWindow addBlockingSubWindow(Widget content, String title, boolean resizable, float x, float y, CssLengthUnit width, CssLengthUnit height, boolean autoShrinkHeight) {
        return addBlockingSubWindow(content, IComponent.literal(title), resizable, x, y, width, height, autoShrinkHeight);
    }

    @Override
    public void setInnerOverlayProvider(Supplier<Widget> provider) {
        if(this.widget == null) return;
        this.widget.inner.setExtensionOverlayProvider(this.extensionId, provider);
    }

    @Override
    public void refreshInnerOverlay() {
        if(this.widget == null) return;
        this.widget.inner.refreshExtensionOverlay(this.extensionId);
    }

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

    @Override
    public @Nullable Vector3f projScreen2World(double screenX, double screenY) {
        if(this.widget == null) return null;
        return this.widget.inner.projScreen2World(screenX, screenY);
    }

    @Override
    public @Nullable Vector2f projWorld2Screen(Vector3f worldPos) {
        if(this.widget == null) return null;
        return this.widget.inner.projWorld2Screen(worldPos);
    }

    @Override
    public @Nullable LevelChunkStorage currentStorage() {
        return TerrainChunkManager.INSTANCE.getCurrentLevelChunkStorage();
    }

    @Override
    public WorldTerrainWidgetInner inner() {
        if(this.widget == null) {
            throw new IllegalStateException("No world terrain widget is attached to this service");
        }
        return this.widget.inner;
    }

    private @Nullable WorldMapUiStateStorage uiStateStorage() {
        var storage = this.currentStorage();
        if (storage == null) {
            return null;
        }
        var extensionStorage = storage.getExtensionStorage(WorldMapUiStateStorage.EXTENSION_ID);
        if (extensionStorage instanceof WorldMapUiStateStorage uiStateStorage) {
            return uiStateStorage;
        }
        var uiStateStorage = new WorldMapUiStateStorage();
        storage.registerExtensionStorage(uiStateStorage);
        return uiStateStorage;
    }

    private String stateKey(String key) {
        if (this.extensionId == null || this.extensionId.isEmpty()) {
            return key;
        }
        return this.extensionId + ":" + key;
    }
}
