package com.xkball.x3dmap.client.map.viewport;

import com.xkball.x3dmap.api.client.viewport.IMapViewport;
import com.xkball.x3dmap.api.client.viewport.IMapViewportManager;
import com.xkball.x3dmap.api.client.viewport.MapViewportSpec;
import com.xkball.x3dmap.client.map.runtime.X3dMapRuntimeImpl;
import com.xkball.x3dmap.ui.widget.WorldTerrainWidgetInner;
import com.xkball.xklibmc.annotation.NonNullByDefault;

import java.util.ArrayList;
import java.util.List;

@NonNullByDefault
public final class MapViewportManagerImpl implements IMapViewportManager {
    
    private final X3dMapRuntimeImpl runtime;
    private final List<IMapViewport> viewports = new ArrayList<>();
    
    public MapViewportManagerImpl(X3dMapRuntimeImpl runtime) {
        this.runtime = runtime;
    }
    
    @Override
    public IMapViewport create(MapViewportSpec spec) {
        return WorldTerrainWidgetInner.createStandalone(this.runtime, spec);
    }
    
    public void track(IMapViewport viewport) {
        if (!this.viewports.contains(viewport)) {
            this.viewports.add(viewport);
        }
    }
    
    public void release(IMapViewport viewport) {
        this.viewports.remove(viewport);
    }
    
    public void closeAll() {
        for (var viewport : List.copyOf(this.viewports).reversed()) {
            viewport.close();
        }
        this.viewports.clear();
    }
}
