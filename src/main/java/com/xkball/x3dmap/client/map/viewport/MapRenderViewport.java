package com.xkball.x3dmap.client.map.viewport;

import com.xkball.x3dmap.api.client.gui.input.MapInputEvent;
import com.xkball.x3dmap.api.client.gui.input.MapInputResult;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.x3dmap.api.client.render.IMapLayerHost;
import com.xkball.x3dmap.api.client.viewport.IMapCamera;
import com.xkball.x3dmap.api.client.viewport.IMapProjection;
import com.xkball.x3dmap.api.client.viewport.IMapViewport;
import com.xkball.x3dmap.api.client.viewport.MapCameraState;
import com.xkball.x3dmap.api.client.viewport.MapRay;
import com.xkball.x3dmap.client.map.render.MapLayerFrame;
import com.xkball.x3dmap.client.map.render.MapLayerHostImpl;
import com.xkball.x3dmap.client.map.runtime.X3dMapRuntimeImpl;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

@NonNullByDefault
public final class MapRenderViewport implements IMapViewport, IMapProjection {
    
    private final X3dMapRuntimeImpl runtime;
    private final ResourceKey<Level> dimension;
    private final Identifier preset;
    private final Widget widget;
    private final MapCameraImpl camera;
    private final MapLayerHostImpl layers;
    private MapCameraState cameraState;
    private @Nullable IMapFrame frame;
    private boolean invalidated = true;
    private boolean closed;
    
    public MapRenderViewport(X3dMapRuntimeImpl runtime, Widget widget, ResourceKey<Level> dimension, Identifier preset, MapCameraState cameraState) {
        this.runtime = runtime;
        this.dimension = dimension;
        this.preset = preset;
        this.widget = widget;
        this.cameraState = cameraState;
        this.camera = new MapCameraImpl(() -> this.cameraState, state -> this.cameraState = state, this::invalidate);
        this.layers = new MapLayerHostImpl(runtime, this);
        this.layers.addRegisteredLayers(runtime.layerRegistry());
        runtime.viewportManagerImpl().track(this);
    }
    
    public MapLayerFrame prepare(MapFrameSnapshot frame) {
        this.acceptFrame(frame);
        this.invalidated = false;
        return this.layers.prepare(frame);
    }
    
    public MapLayerFrame prepare3d(MapFrameSnapshot frame) {
        this.acceptFrame(frame);
        this.invalidated = false;
        return this.layers.prepare3d(frame);
    }
    
    public MapLayerFrame prepare2d(MapFrameSnapshot frame) {
        this.acceptFrame(frame);
        return this.layers.prepare2d(frame);
    }
    
    public boolean invalidated() {
        return this.invalidated;
    }
    
    public MapInputResult handle(MapInputEvent event) {
        return this.layers.handle(event, new MapInputContextImpl(this, event));
    }
    
    public void tick() {
        this.layers.tick();
    }
    
    private void acceptFrame(MapFrameSnapshot frame) {
        if (!this.camera.externallyControlled()) {
            this.cameraState = frame.camera();
        }
        this.frame = frame;
    }
    
    @Override
    public ResourceKey<Level> dimension() {
        return this.dimension;
    }
    
    @Override
    public Identifier preset() {
        return this.preset;
    }
    
    @Override
    public Widget widget() {
        return this.widget;
    }
    
    @Override
    public IMapCamera camera() {
        return this.camera;
    }
    
    @Override
    public IMapProjection projection() {
        return this;
    }
    
    @Override
    public IMapLayerHost layers() {
        return this.layers;
    }
    
    @Override
    public void invalidate() {
        this.invalidated = true;
    }
    
    @Override
    public @Nullable Vector2f worldToScreen(Vector3fc worldPosition) {
        return this.frame == null ? null : this.frame.worldToScreen(worldPosition);
    }
    
    @Override
    public @Nullable MapRay screenRay(double screenX, double screenY) {
        return this.frame == null ? null : this.frame.screenRay(screenX, screenY);
    }
    
    @Override
    public @Nullable Vector3f screenToTerrain(double screenX, double screenY) {
        return this.frame == null ? null : this.frame.screenToTerrain(screenX, screenY);
    }
    
    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            this.layers.close();
            this.camera.close();
            this.frame = null;
            this.runtime.viewportManagerImpl().release(this);
        }
    }
}
