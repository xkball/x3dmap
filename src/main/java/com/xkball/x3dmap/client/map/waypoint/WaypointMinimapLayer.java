package com.xkball.x3dmap.client.map.waypoint;

import com.xkball.x3dmap.api.client.render.IMap2dLayer;
import com.xkball.x3dmap.api.client.render.IMap2dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMap2dRenderContext;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.x3dmap.api.client.render.IMapLayerContext;
import com.xkball.x3dmap.client.map.plugin.X3dMapBuiltinPlugin;
import com.xkball.x3dmap.client.map.storage.BuiltinMapDataTypes;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklib.resource.ResourceLocation;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.x3d.backend.b3d.B3dGuiGraphics;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NonNullByDefault
public final class WaypointMinimapLayer implements IMap2dLayer {

    private static final ResourceLocation PINNED_ICON = VanillaUtils.modrl("icon/pinned");
    private static final String VISIBILITY_STATE_KEY = VanillaUtils.modRL("waypoint") + ":visible";
    private final IMapLayerContext layerContext;
    private final Runnable invalidator;
    private @Nullable WaypointStorage observedStorage;

    public WaypointMinimapLayer(IMapLayerContext layerContext) {
        this.layerContext = layerContext;
        this.invalidator = layerContext::invalidate;
    }

    @Override
    public @Nullable IMap2dRenderCommand extract(IMapFrame frame) {
        var storage = this.layerContext.runtime().storage().levelData(frame.dimension())
                .map(access -> access.get(BuiltinMapDataTypes.WAYPOINTS).value())
                .orElse(null);
        if (storage == null) {
            return null;
        }
        this.observe(storage);
        var waypoints = storage.waypoints().stream().filter(waypoint -> !waypoint.hidden()).toList();
        if (waypoints.isEmpty()) {
            return null;
        }
        return context -> {
            if (this.visible(frame)) {
                render(context, waypoints);
            }
        };
    }

    @Override
    public void close() {
        if (this.observedStorage != null) {
            this.observedStorage.removeDirtyListener(this.invalidator);
            this.observedStorage = null;
        }
    }

    private void observe(WaypointStorage storage) {
        if (this.observedStorage == storage) {
            return;
        }
        if (this.observedStorage != null) {
            this.observedStorage.removeDirtyListener(this.invalidator);
        }
        this.observedStorage = storage;
        storage.addDirtyListener(this.invalidator);
    }

    private boolean visible(IMapFrame frame) {
        return this.layerContext.runtime().storage().levelData(frame.dimension())
                .map(access -> access.get(BuiltinMapDataTypes.UI_STATE).value().getBoolean(VISIBILITY_STATE_KEY, true))
                .orElse(true);
    }

    private static void render(IMap2dRenderContext context, List<Waypoint> waypoints) {
        if (!(context.graphics() instanceof B3dGuiGraphics graphics)) {
            return;
        }
        for (var waypoint : waypoints) {
            var pos = waypoint.pos();
            var screen = context.frame().worldToScreen(new Vector3f(pos.getX(), pos.getY(), pos.getZ()));
            if (screen == null) {
                continue;
            }
            var textHeight = 7.0f;
            var iconSize = 7.0f;
            var textWidth = graphics.defaultFont().width(waypoint.name(), textHeight);
            var width = iconSize + textWidth + 3;
            var x = screen.x;
            var y = screen.y - 9;
            graphics.fillRounded(x, y, x + width, y + 9, 0xA0000000, 2);
            graphics.blitSprite(PINNED_ICON, x + 1, y + 1, iconSize, iconSize, waypoint.color());
            graphics.drawString(waypoint.name(), x + iconSize + 2, y + 1, 0xFFFFFFFF, textHeight);
        }
    }
}
