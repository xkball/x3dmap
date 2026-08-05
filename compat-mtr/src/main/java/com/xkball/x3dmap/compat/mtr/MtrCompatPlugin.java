package com.xkball.x3dmap.compat.mtr;

import com.xkball.x3dmap.api.client.IX3dMapPlugin;
import com.xkball.x3dmap.api.client.X3dMapPlugin;
import com.xkball.x3dmap.api.client.registration.IMapGuiRegistration;
import com.xkball.x3dmap.api.client.registration.IMapLayerRegistration;
import com.xkball.x3dmap.api.client.render.Map2dLayerPhase;
import com.xkball.x3dmap.api.client.render.Map2dLayerSpec;
import com.xkball.x3dmap.api.client.render.Map3dLayerPhase;
import com.xkball.x3dmap.api.client.render.Map3dLayerSpec;
import com.xkball.x3dmap.api.client.render.MapViewportPresets;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;

import java.util.Set;

@X3dMapPlugin
@NonNullByDefault
public final class MtrCompatPlugin implements IX3dMapPlugin {
    
    private static final String MTR_MOD_ID = "mtr";
    private static final Identifier PLUGIN_ID = X3dMapMtrCompat.id("plugin");
    public static final Identifier ROUTE_LAYER = X3dMapMtrCompat.id("routes");
    public static final Identifier STATION_LAYER = X3dMapMtrCompat.id("stations");
    
    @Override
    public Identifier getPluginUid() {
        return PLUGIN_ID;
    }
    
    @Override
    public void registerGui(IMapGuiRegistration registration) {
        if (ModList.get().isLoaded(MTR_MOD_ID)) {
            registration.addScreenExtension(X3dMapMtrCompat.id("screen"), 10, MtrScreenExtension::new);
        }
    }
    
    @Override
    public void registerLayers(IMapLayerRegistration registration) {
        if (!ModList.get().isLoaded(MTR_MOD_ID)) {
            return;
        }
        var worldMapAndMinimap = Set.of(MapViewportPresets.WORLD_MAP, MapViewportPresets.MINIMAP);
        registration.add3d(
                new Map3dLayerSpec(ROUTE_LAYER, worldMapAndMinimap, Map3dLayerPhase.AFTER_TERRAIN, 5, 5, true),
                MtrRouteLayer::new
        );
        registration.add2d(
                new Map2dLayerSpec(STATION_LAYER, worldMapAndMinimap, Map2dLayerPhase.CONTENT, 5, 5, true),
                _ -> new MtrStationLayer()
        );
    }
}
