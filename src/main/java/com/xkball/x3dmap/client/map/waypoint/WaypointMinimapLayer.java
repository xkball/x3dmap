package com.xkball.x3dmap.client.map.waypoint;

import com.xkball.x3dmap.api.client.render.IMap2dLayer;
import com.xkball.x3dmap.api.client.render.IMap2dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMap2dRenderContext;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.x3dmap.api.client.render.IMapLayerContext;
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
        return context -> render(context, waypoints);
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

    private static void render(IMap2dRenderContext context, List<Waypoint> waypoints) {
        if (!(context.graphics() instanceof B3dGuiGraphics graphics)) {
            return;
        }
        for (var waypoint : waypoints) {
            var pos = waypoint.pos();
            var screen = context.frame().worldToScreen(new Vector3f(pos.getX() + 0.5f, pos.getY() + 1.0f, pos.getZ() + 0.5f));
            if (screen == null) {
                continue;
            }
            var textHeight = 7.0f;
            var iconSize = 7.0f;
            var textWidth = graphics.defaultFont().width(waypoint.name(), textHeight);
            var width = iconSize + textWidth + 3;
            var x = screen.x - width / 2;
            var y = screen.y - 11;
            graphics.fillRounded(x, y, x + width, y + 9, 0xA0000000, 2);
            graphics.blitSprite(PINNED_ICON, x + 1, y + 1, iconSize, iconSize, waypoint.color());
            graphics.drawString(waypoint.name(), x + iconSize + 2, y + 1, 0xFFFFFFFF, textHeight);
        }
    }
}
