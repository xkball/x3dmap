package com.xkball.x3dmap.client.map.viewport;

import com.xkball.x3dmap.api.client.gui.input.IMapInputContext;
import com.xkball.x3dmap.api.client.gui.input.MapInputEvent;
import com.xkball.x3dmap.api.client.viewport.IMapViewport;
import com.xkball.x3dmap.api.client.viewport.MapRay;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

@NonNullByDefault
public final class MapInputContextImpl implements IMapInputContext {

    private final IMapViewport viewport;
    private final @Nullable MapRay screenRay;
    private final @Nullable Vector3f terrainHit;

    public MapInputContextImpl(IMapViewport viewport, MapInputEvent event) {
        this.viewport = viewport;
        var pointer = pointer(event);
        if (pointer == null) {
            this.screenRay = null;
            this.terrainHit = null;
        } else {
            this.screenRay = viewport.projection().screenRay(pointer.x, pointer.y);
            this.terrainHit = viewport.projection().screenToTerrain(pointer.x, pointer.y);
        }
    }

    @Override
    public IMapViewport viewport() {
        return this.viewport;
    }

    @Override
    public @Nullable MapRay screenRay() {
        return this.screenRay;
    }

    @Override
    public @Nullable Vector3f terrainHit() {
        return this.terrainHit == null ? null : new Vector3f(this.terrainHit);
    }

    @Override
    public void invalidate() {
        this.viewport.invalidate();
    }

    private static @Nullable Pointer pointer(MapInputEvent event) {
        return switch (event) {
            case MapInputEvent.MouseMoved moved -> new Pointer(moved.x(), moved.y());
            case MapInputEvent.MouseClicked clicked -> new Pointer(clicked.event().x(), clicked.event().y());
            case MapInputEvent.MouseReleased released -> new Pointer(released.event().x(), released.event().y());
            case MapInputEvent.MouseDragged dragged -> new Pointer(dragged.event().x(), dragged.event().y());
            case MapInputEvent.MouseScrolled scrolled -> new Pointer(scrolled.x(), scrolled.y());
            default -> null;
        };
    }

    private record Pointer(double x, double y) {
    }
}
