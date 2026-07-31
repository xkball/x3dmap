package com.xkball.x3dmap.compat.smu;

import com.xkball.x3dmap.api.client.IX3dMapPlugin;
import com.xkball.x3dmap.api.client.X3dMapPlugin;
import com.xkball.x3dmap.api.client.registration.IMapGuiRegistration;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;

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

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(X3dMapSmuCompat.MODID, path);
    }
}
