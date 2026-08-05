package com.xkball.x3dmap.compat.smu;

import com.xkball.x3dmap.api.client.IX3dMapPlugin;
import com.xkball.x3dmap.api.client.X3dMapPlugin;
import com.xkball.x3dmap.api.client.registration.IMapGuiRegistration;
import com.xkball.x3dmap.api.client.registration.IMapLayerRegistration;
import com.xkball.x3dmap.api.client.render.Map2dLayerPhase;
import com.xkball.x3dmap.api.client.render.Map2dLayerSpec;
import com.xkball.x3dmap.api.client.render.MapViewportPresets;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;

import java.util.Set;

@X3dMapPlugin
@NonNullByDefault
public final class SmuCompatPlugin implements IX3dMapPlugin {

    private static final String SMU_MOD_ID = "exhibition_portal";
    private static final Identifier PLUGIN_ID = id("plugin");
    public static final Identifier LABEL_TOGGLE_SPRITE = VanillaUtils.modRL("icon/teacon");

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerGui(IMapGuiRegistration registration) {
        if (ModList.get().isLoaded(SMU_MOD_ID)) {
            registration.addScreenExtension(id("screen"), 10, SmuScreenExtension::new);
        }
    }

    @Override
    public void registerLayers(IMapLayerRegistration registration) {
        if (ModList.get().isLoaded(SMU_MOD_ID)) {
            registration.add2d(
                    new Map2dLayerSpec(id("minimap_exhibitions"), Set.of(MapViewportPresets.MINIMAP), Map2dLayerPhase.CONTENT, 20, 0, true),
                    _ -> new SmuMinimapLayer()
            );
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(X3dMapSmuCompat.MODID, path);
    }
}
