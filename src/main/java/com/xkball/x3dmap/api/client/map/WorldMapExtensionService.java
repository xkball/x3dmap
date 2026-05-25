package com.xkball.x3dmap.api.client.map;

import com.xkball.x3dmap.client.terrain.LevelChunkStorage;
import com.xkball.x3dmap.ui.widget.WorldTerrainWidgetInner;
import com.xkball.xklib.ui.css.property.value.CssLengthUnit;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklib.ui.widget.container.WindowedContainer;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public interface WorldMapExtensionService {
    
    String extensionId();
    
    WorldMapExtensionService scope(String extensionId);
    
    void addLeftBarWidget(Widget widget);
    
    void addTopBar1Widget(Widget widget);
    
    void addTopBar2Widget(Widget widget);
    
    WindowedContainer.SubWindow addSubWindow(Widget content, CssLengthUnit width, CssLengthUnit height);
    
    WindowedContainer.SubWindow addSubWindow(Widget content, float x, float y, CssLengthUnit width, CssLengthUnit height);
    
    WindowedContainer.SubWindow addSubWindow(Widget content, IComponent title, boolean resizable, CssLengthUnit width, CssLengthUnit height);
    
    WindowedContainer.SubWindow addSubWindow(Widget content, IComponent title, boolean resizable, float x, float y, CssLengthUnit width, CssLengthUnit height);
    
    WindowedContainer.SubWindow addSubWindow(Widget content, String title, boolean resizable, CssLengthUnit width, CssLengthUnit height);
    
    WindowedContainer.SubWindow addSubWindow(Widget content, String title, boolean resizable, float x, float y, CssLengthUnit width, CssLengthUnit height);
    
    default WindowedContainer.SubWindow addBlockingSubWindow(Widget content, CssLengthUnit width, CssLengthUnit height) {
        return addBlockingSubWindow(content, IComponent.literal(""), false, width, height);
    }
    
    WindowedContainer.SubWindow addBlockingSubWindow(Widget content, IComponent title, boolean resizable, CssLengthUnit width, CssLengthUnit height);
    
    WindowedContainer.SubWindow addBlockingSubWindow(Widget content, String title, boolean resizable, CssLengthUnit width, CssLengthUnit height);
    
    default WindowedContainer.SubWindow addBlockingSubWindow(Widget content, IComponent title, boolean resizable, float x, float y, CssLengthUnit width, CssLengthUnit height) {
        return addBlockingSubWindow(content, title, resizable, x, y, width, height, true);
    }
    
    default WindowedContainer.SubWindow addBlockingSubWindow(Widget content, String title, boolean resizable, float x, float y, CssLengthUnit width, CssLengthUnit height) {
        return addBlockingSubWindow(content, IComponent.literal(title), resizable, x, y, width, height, true);
    }
    
    WindowedContainer.SubWindow addBlockingSubWindow(Widget content, IComponent title, boolean resizable, float x, float y, CssLengthUnit width, CssLengthUnit height, boolean autoShrinkHeight);
    
    WindowedContainer.SubWindow addBlockingSubWindow(Widget content, String title, boolean resizable, float x, float y, CssLengthUnit width, CssLengthUnit height, boolean autoShrinkHeight);
    
    void setInnerOverlayProvider(Supplier<Widget> provider);
    
    void refreshInnerOverlay();
    
    boolean containsState(String key);
    
    void removeState(String key);
    
    boolean getBooleanState(String key, boolean defaultValue);
    
    void setBooleanState(String key, boolean value);
    
    int getIntState(String key, int defaultValue);
    
    void setIntState(String key, int value);
    
    float getFloatState(String key, float defaultValue);
    
    void setFloatState(String key, float value);
    
    String getStringState(String key, String defaultValue);
    
    void setStringState(String key, String value);
    
    @Nullable Vector3f projScreen2World(double screenX, double screenY);
    
    @Nullable Vector2f projWorld2Screen(Vector3f worldPos);
    
    @Nullable LevelChunkStorage currentStorage();
    
    WorldTerrainWidgetInner inner();
}
