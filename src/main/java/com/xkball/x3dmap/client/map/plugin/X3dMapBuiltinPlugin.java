package com.xkball.x3dmap.client.map.plugin;

import com.xkball.x3dmap.api.client.IX3dMapPlugin;
import com.xkball.x3dmap.api.client.X3dMapPlugin;
import com.xkball.x3dmap.api.client.registration.IMapGuiRegistration;
import com.xkball.x3dmap.api.client.registration.IMapLayerRegistration;
import com.xkball.x3dmap.api.client.registration.IMapStorageRegistration;
import com.xkball.x3dmap.api.client.render.Map2dLayerPhase;
import com.xkball.x3dmap.api.client.render.Map2dLayerSpec;
import com.xkball.x3dmap.api.client.render.Map3dLayerPhase;
import com.xkball.x3dmap.api.client.render.Map3dLayerSpec;
import com.xkball.x3dmap.api.client.render.MapViewportPresets;
import com.xkball.x3dmap.client.map.minimap.MinimapExtension;
import com.xkball.x3dmap.client.map.selection.SelectionExtension;
import com.xkball.x3dmap.client.map.selection.SelectionOverlayRenderer;
import com.xkball.x3dmap.client.map.storage.BuiltinMapDataTypes;
import com.xkball.x3dmap.client.map.waypoint.WaypointExtension;
import com.xkball.x3dmap.client.render.pip.layers.CameraTargetRenderer;
import com.xkball.x3dmap.client.render.pip.layers.CompassMapLayer;
import com.xkball.x3dmap.client.render.pip.layers.GridRenderer;
import com.xkball.x3dmap.client.render.pip.layers.PlayerOnMapRenderer;
import com.xkball.x3dmap.client.render.pip.layers.PlayerHeadsMapLayer;
import com.xkball.x3dmap.client.render.pip.layers.TerrainRenderer;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

import java.util.Set;

@X3dMapPlugin
@NonNullByDefault
public final class X3dMapBuiltinPlugin implements IX3dMapPlugin {

    private static final Identifier UID = VanillaUtils.modRL("core");

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
        registration.addScreenExtension(VanillaUtils.modRL("waypoint"), 0, WaypointExtension::new);
        registration.addScreenExtension(VanillaUtils.modRL("minimap"), 0, MinimapExtension::new);
        registration.addScreenExtension(VanillaUtils.modRL("selection"), 1, SelectionExtension::new);
    }

    @Override
    public void registerLayers(IMapLayerRegistration registration) {
        var worldMap = Set.of(MapViewportPresets.WORLD_MAP);
        var worldMapAndMinimap = Set.of(MapViewportPresets.WORLD_MAP, MapViewportPresets.MINIMAP);
        registration.add3d(new Map3dLayerSpec(VanillaUtils.modRL("terrain"), worldMapAndMinimap, Map3dLayerPhase.TERRAIN, 0, 0, true), _ -> new TerrainRenderer());
        registration.add3d(new Map3dLayerSpec(VanillaUtils.modRL("grid"), worldMap, Map3dLayerPhase.AFTER_TERRAIN, 0, 0, true), _ -> new GridRenderer());
        registration.add3d(new Map3dLayerSpec(VanillaUtils.modRL("selection"), worldMap, Map3dLayerPhase.AFTER_TERRAIN, 10, 10, true), _ -> new SelectionOverlayRenderer());
        registration.add3d(new Map3dLayerSpec(VanillaUtils.modRL("camera_target"), worldMap, Map3dLayerPhase.AFTER_TERRAIN, 20, 20, false), _ -> new CameraTargetRenderer());
        registration.add3d(new Map3dLayerSpec(VanillaUtils.modRL("player"), worldMap, Map3dLayerPhase.AFTER_TERRAIN, 30, 30, true), _ -> new PlayerOnMapRenderer());
        registration.add2d(new Map2dLayerSpec(VanillaUtils.modRL("player_heads"), worldMap, Map2dLayerPhase.CONTENT, 0, 0, true), _ -> new PlayerHeadsMapLayer());
        registration.add2d(new Map2dLayerSpec(VanillaUtils.modRL("compass"), worldMap, Map2dLayerPhase.FOREGROUND, 0, 0, true), _ -> new CompassMapLayer());
    }

}
