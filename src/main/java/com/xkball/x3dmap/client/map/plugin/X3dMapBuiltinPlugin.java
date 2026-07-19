package com.xkball.x3dmap.client.map.plugin;

import com.xkball.x3dmap.X3dMap;
import com.xkball.x3dmap.api.client.IX3dMapPlugin;
import com.xkball.x3dmap.api.client.X3dMapPlugin;
import com.xkball.x3dmap.api.client.registration.IMapGuiRegistration;
import com.xkball.x3dmap.api.client.registration.IMapLayerRegistration;
import com.xkball.x3dmap.api.client.registration.IMapStorageRegistration;
import com.xkball.x3dmap.api.client.render.MapRenderTarget;
import com.xkball.x3dmap.client.map.minimap.MinimapExtension;
import com.xkball.x3dmap.client.map.selection.SelectionExtension;
import com.xkball.x3dmap.client.map.selection.SelectionOverlayRenderer;
import com.xkball.x3dmap.client.map.storage.BuiltinMapDataTypes;
import com.xkball.x3dmap.client.map.waypoint.WaypointExtension;
import com.xkball.x3dmap.client.render.pip.layers.CameraTargetRenderer;
import com.xkball.x3dmap.client.render.pip.layers.GridRenderer;
import com.xkball.x3dmap.client.render.pip.layers.PlayerOnMapRenderer;
import com.xkball.x3dmap.client.render.pip.layers.TerrainRenderer;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

import java.util.Set;

@X3dMapPlugin
@NonNullByDefault
public final class X3dMapBuiltinPlugin implements IX3dMapPlugin {

    private static final Identifier UID = id("core");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerStorage(IMapStorageRegistration registration) {
        registration.registerLevelData(BuiltinMapDataTypes.UI_STATE);
        registration.registerLevelData(BuiltinMapDataTypes.WAYPOINTS);
    }

    @Override
    public void registerGui(IMapGuiRegistration registration) {
        registration.addScreenExtension(id("waypoint"), 0, WaypointExtension::new);
        registration.addScreenExtension(id("minimap"), 0, MinimapExtension::new);
        registration.addScreenExtension(id("selection"), 1, SelectionExtension::new);
    }

    @Override
    public void registerLayers(IMapLayerRegistration registration) {
        registration.register(id("terrain"), Set.of(MapRenderTarget.WORLD_MAP, MapRenderTarget.MINIMAP), TerrainRenderer::new);
        registration.register(id("grid"), Set.of(MapRenderTarget.WORLD_MAP), GridRenderer::new);
        registration.register(id("player"), Set.of(MapRenderTarget.WORLD_MAP), PlayerOnMapRenderer::new);
        registration.register(id("camera_target"), Set.of(MapRenderTarget.WORLD_MAP), CameraTargetRenderer::new);
        registration.register(id("selection"), Set.of(MapRenderTarget.WORLD_MAP), SelectionOverlayRenderer::new);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(X3dMap.MODID, path);
    }
}
