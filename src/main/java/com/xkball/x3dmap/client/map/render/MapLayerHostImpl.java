package com.xkball.x3dmap.client.map.render;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.api.client.gui.input.IMapInputContext;
import com.xkball.x3dmap.api.client.gui.input.MapInputEvent;
import com.xkball.x3dmap.api.client.gui.input.MapInputResult;
import com.xkball.x3dmap.api.client.render.IMap2dLayer;
import com.xkball.x3dmap.api.client.render.IMap2dLayerFactory;
import com.xkball.x3dmap.api.client.render.IMap3dLayer;
import com.xkball.x3dmap.api.client.render.IMap3dLayerFactory;
import com.xkball.x3dmap.api.client.render.IMapContentLayer;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.x3dmap.api.client.render.IMapLayerHandle;
import com.xkball.x3dmap.api.client.render.IMapLayerHost;
import com.xkball.x3dmap.api.client.render.Map2dLayerSpec;
import com.xkball.x3dmap.api.client.render.Map3dLayerSpec;
import com.xkball.x3dmap.api.client.runtime.IX3dMapRuntime;
import com.xkball.x3dmap.api.client.viewport.IMapViewport;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@NonNullByDefault
public final class MapLayerHostImpl implements IMapLayerHost, AutoCloseable {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final IX3dMapRuntime runtime;
    private final IMapViewport viewport;
    private final List<ThreeDimensionalEntry> threeDimensionalEntries = new ArrayList<>();
    private final List<TwoDimensionalEntry> twoDimensionalEntries = new ArrayList<>();
    private @Nullable LayerEntry capturedEntry;
    private int capturedButton = -1;
    private boolean closed;

    public MapLayerHostImpl(IX3dMapRuntime runtime, IMapViewport viewport) {
        this.runtime = runtime;
        this.viewport = viewport;
    }

    public void addRegisteredLayers(MapLayerRegistry registry) {
        for (var definition : registry.threeDimensionalDefinitions(viewport.preset())) {
            try {
                this.add3d(definition.spec(), definition.factory());
            } catch (Exception e) {
                LOGGER.error("Failed to create registered 3D map layer {}", definition.spec().id(), e);
            }
        }
        for (var definition : registry.twoDimensionalDefinitions(viewport.preset())) {
            try {
                this.add2d(definition.spec(), definition.factory());
            } catch (Exception e) {
                LOGGER.error("Failed to create registered 2D map layer {}", definition.spec().id(), e);
            }
        }
    }

    @Override
    public IMapLayerHandle add3d(Map3dLayerSpec spec, IMap3dLayerFactory factory) {
        this.checkOpen();
        this.checkDuplicate(spec.id());
        var context = new MapLayerContextImpl(spec.id(), this.runtime, this.viewport);
        var entry = new ThreeDimensionalEntry(spec, factory.create(context));
        this.threeDimensionalEntries.add(entry);
        this.threeDimensionalEntries.sort(Comparator
                .comparing((ThreeDimensionalEntry value) -> value.spec.phase())
                .thenComparingInt(value -> value.spec.order())
                .thenComparing(value -> value.spec.id().toString()));
        this.viewport.invalidate();
        return new LayerHandleImpl(entry);
    }

    @Override
    public IMapLayerHandle add2d(Map2dLayerSpec spec, IMap2dLayerFactory factory) {
        this.checkOpen();
        this.checkDuplicate(spec.id());
        var context = new MapLayerContextImpl(spec.id(), this.runtime, this.viewport);
        var entry = new TwoDimensionalEntry(spec, factory.create(context));
        this.twoDimensionalEntries.add(entry);
        this.twoDimensionalEntries.sort(Comparator
                .comparing((TwoDimensionalEntry value) -> value.spec.phase())
                .thenComparingInt(value -> value.spec.order())
                .thenComparing(value -> value.spec.id().toString()));
        this.viewport.invalidate();
        return new LayerHandleImpl(entry);
    }

    @Override
    public List<Identifier> threeDimensionalLayers() {
        return this.threeDimensionalEntries.stream().map(entry -> entry.spec.id()).toList();
    }

    @Override
    public List<Identifier> twoDimensionalLayers() {
        return this.twoDimensionalEntries.stream().map(entry -> entry.spec.id()).toList();
    }

    @Override
    public boolean visible(Identifier id) {
        var entry = this.find(id);
        return entry != null && entry.visible;
    }

    @Override
    public void setVisible(Identifier id, boolean visible) {
        var entry = this.find(id);
        if (entry != null && entry.visible != visible) {
            entry.visible = visible;
            if (!visible && this.capturedEntry == entry) {
                this.releasePointerCapture();
            }
            this.viewport.invalidate();
        }
    }

    public MapLayerFrame prepare(IMapFrame frame) {
        var threeDimensional = this.prepare3d(frame);
        var twoDimensional = this.prepare2d(frame);
        return new MapLayerFrame(threeDimensional.threeDimensionalLayers(), twoDimensional.twoDimensionalLayers());
    }

    public MapLayerFrame prepare3d(IMapFrame frame) {
        var threeDimensionalLayers = new ArrayList<PreparedMap3dLayer>();
        for (var entry : this.threeDimensionalEntries) {
            if (!entry.visible()) {
                continue;
            }
            try {
                var command = entry.layer.prepareRender(frame);
                if (command != null) {
                    threeDimensionalLayers.add(new PreparedMap3dLayer(entry.spec, command));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to extract 3D map layer {}", entry.spec.id(), e);
            }
        }
        return new MapLayerFrame(threeDimensionalLayers, List.of());
    }

    public MapLayerFrame prepare2d(IMapFrame frame) {
        var twoDimensionalLayers = new ArrayList<PreparedMap2dLayer>();
        for (var entry : this.twoDimensionalEntries) {
            if (!entry.visible()) {
                continue;
            }
            try {
                var command = entry.layer.extract(frame);
                if (command != null) {
                    twoDimensionalLayers.add(new PreparedMap2dLayer(entry.spec, command));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to extract 2D map layer {}", entry.spec.id(), e);
            }
        }
        return new MapLayerFrame(List.of(), twoDimensionalLayers);
    }

    public MapInputResult handle(MapInputEvent event, IMapInputContext context) {
        var captured = this.capturedEntry;
        if (captured != null && !captured.visible) {
            this.releasePointerCapture();
            captured = null;
        }
        if (captured != null && isPointerEvent(event)) {
            var result = this.handle(captured, event, context);
            if (event instanceof MapInputEvent.MouseReleased released
                    && (this.capturedButton < 0 || released.event().button() == this.capturedButton)) {
                this.releasePointerCapture();
            }
            return result == MapInputResult.PASS ? MapInputResult.HANDLED : result;
        }
        var entries = new ArrayList<LayerEntry>();
        entries.addAll(this.twoDimensionalEntries);
        entries.addAll(this.threeDimensionalEntries);
        entries.sort(Comparator.comparingInt(LayerEntry::inputOrder).reversed());
        for (var entry : entries) {
            if (!entry.visible) {
                continue;
            }
            var result = this.handle(entry, event, context);
            if (result == MapInputResult.CAPTURE_POINTER) {
                this.capturedEntry = entry;
                this.capturedButton = pointerButton(event);
                return result;
            }
            if (result == MapInputResult.HANDLED) {
                return result;
            }
        }
        return MapInputResult.PASS;
    }

    public void tick() {
        for (var entry : this.allEntries()) {
            try {
                entry.layer().tick();
            } catch (Exception e) {
                LOGGER.error("Failed to tick map layer {}", entry.id(), e);
            }
        }
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        var entries = this.allEntries();
        for (var entry : entries.reversed()) {
            this.close(entry);
        }
        this.threeDimensionalEntries.clear();
        this.twoDimensionalEntries.clear();
        this.releasePointerCapture();
    }

    private MapInputResult handle(LayerEntry entry, MapInputEvent event, IMapInputContext context) {
        try {
            return entry.layer().handle(event, context);
        } catch (Exception e) {
            LOGGER.error("Failed to dispatch map input to layer {}", entry.id(), e);
            return MapInputResult.PASS;
        }
    }

    private void close(LayerEntry entry) {
        try {
            entry.layer().close();
        } catch (Exception e) {
            LOGGER.error("Failed to close map layer {}", entry.id(), e);
        }
    }

    private void remove(LayerEntry entry) {
        if (entry.removed) {
            return;
        }
        entry.removed = true;
        if (this.capturedEntry == entry) {
            this.releasePointerCapture();
        }
        this.threeDimensionalEntries.remove(entry);
        this.twoDimensionalEntries.remove(entry);
        this.close(entry);
        this.viewport.invalidate();
    }

    private @Nullable LayerEntry find(Identifier id) {
        for (var entry : this.allEntries()) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    private List<LayerEntry> allEntries() {
        var result = new ArrayList<LayerEntry>();
        result.addAll(this.threeDimensionalEntries);
        result.addAll(this.twoDimensionalEntries);
        return result;
    }

    private void checkDuplicate(Identifier id) {
        if (this.find(id) != null) {
            throw new IllegalArgumentException("Duplicate map layer in viewport: " + id);
        }
    }

    private void checkOpen() {
        if (this.closed) {
            throw new IllegalStateException("Map layer host is closed");
        }
    }

    private void releasePointerCapture() {
        this.capturedEntry = null;
        this.capturedButton = -1;
    }

    private static boolean isPointerEvent(MapInputEvent event) {
        return event instanceof MapInputEvent.MouseMoved
                || event instanceof MapInputEvent.MouseClicked
                || event instanceof MapInputEvent.MouseReleased
                || event instanceof MapInputEvent.MouseDragged
                || event instanceof MapInputEvent.MouseScrolled;
    }

    private static int pointerButton(MapInputEvent event) {
        return switch (event) {
            case MapInputEvent.MouseClicked clicked -> clicked.event().button();
            case MapInputEvent.MouseDragged dragged -> dragged.event().button();
            default -> -1;
        };
    }

    private abstract static class LayerEntry {

        private boolean visible;
        private boolean removed;

        private LayerEntry(boolean visible) {
            this.visible = visible;
        }

        abstract Identifier id();

        abstract int inputOrder();

        abstract IMapContentLayer layer();

        final boolean visible() {
            return this.visible;
        }
    }

    private static final class ThreeDimensionalEntry extends LayerEntry {

        private final Map3dLayerSpec spec;
        private final IMap3dLayer layer;

        private ThreeDimensionalEntry(Map3dLayerSpec spec, IMap3dLayer layer) {
            super(spec.visibleByDefault());
            this.spec = spec;
            this.layer = layer;
        }

        @Override
        Identifier id() {
            return this.spec.id();
        }

        @Override
        int inputOrder() {
            return this.spec.inputOrder();
        }

        @Override
        IMapContentLayer layer() {
            return this.layer;
        }
    }

    private static final class TwoDimensionalEntry extends LayerEntry {

        private final Map2dLayerSpec spec;
        private final IMap2dLayer layer;

        private TwoDimensionalEntry(Map2dLayerSpec spec, IMap2dLayer layer) {
            super(spec.visibleByDefault());
            this.spec = spec;
            this.layer = layer;
        }

        @Override
        Identifier id() {
            return this.spec.id();
        }

        @Override
        int inputOrder() {
            return this.spec.inputOrder();
        }

        @Override
        IMapContentLayer layer() {
            return this.layer;
        }
    }

    private final class LayerHandleImpl implements IMapLayerHandle {

        private final LayerEntry entry;

        private LayerHandleImpl(LayerEntry entry) {
            this.entry = entry;
        }

        @Override
        public Identifier id() {
            return this.entry.id();
        }

        @Override
        public boolean visible() {
            return !this.entry.removed && this.entry.visible;
        }

        @Override
        public void setVisible(boolean visible) {
            if (!this.entry.removed && this.entry.visible != visible) {
                this.entry.visible = visible;
                if (!visible && MapLayerHostImpl.this.capturedEntry == this.entry) {
                    MapLayerHostImpl.this.releasePointerCapture();
                }
                MapLayerHostImpl.this.viewport.invalidate();
            }
        }

        @Override
        public void invalidate() {
            if (!this.entry.removed) {
                MapLayerHostImpl.this.viewport.invalidate();
            }
        }

        @Override
        public void close() {
            MapLayerHostImpl.this.remove(this.entry);
        }
    }
}
