package com.xkball.x3dmap.client.map.gui;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.api.client.gui.IMapScreenExtension;
import com.xkball.x3dmap.api.client.gui.input.MapInputEvent;
import com.xkball.x3dmap.client.map.runtime.X3dMapRuntimeImpl;
import com.xkball.x3dmap.ui.widget.WorldTerrainWidget;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@NonNullByDefault
public final class MapScreenSession implements AutoCloseable {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final List<ExtensionEntry> extensions = new ArrayList<>();

    public MapScreenSession(MapGuiRegistry registry, X3dMapRuntimeImpl runtime, WorldTerrainWidget widget) {
        var gui = new MapGuiImpl(widget);
        for (var definition : registry.definitions()) {
            try {
                var context = new MapScreenContextImpl(definition.id(), runtime, widget.inner, gui);
                var extension = definition.factory().create(context);
                extension.onOpen();
                this.extensions.add(new ExtensionEntry(definition.id().toString(), extension));
            } catch (Exception e) {
                LOGGER.error("Failed to open map screen extension {}", definition.id(), e);
            }
        }
    }

    public boolean handle(MapInputEvent event) {
        for (var entry : this.extensions) {
            try {
                if (entry.extension().handle(event)) {
                    return true;
                }
            } catch (Exception e) {
                LOGGER.error("Failed to dispatch map input to extension {}", entry.id(), e);
            }
        }
        return false;
    }

    public void tick() {
        for (var entry : this.extensions) {
            try {
                entry.extension().tick();
            } catch (Exception e) {
                LOGGER.error("Failed to tick map screen extension {}", entry.id(), e);
            }
        }
    }

    @Override
    public void close() {
        for (var entry : this.extensions.reversed()) {
            try {
                entry.extension().close();
            } catch (Exception e) {
                LOGGER.error("Failed to close map screen extension {}", entry.id(), e);
            }
        }
        this.extensions.clear();
    }

    private record ExtensionEntry(String id, IMapScreenExtension extension) {
    }
}
