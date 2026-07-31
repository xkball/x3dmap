package com.xkball.x3dmap.compat.smu;

import com.xkball.x3dmap.api.client.gui.IMapScreenContext;
import com.xkball.x3dmap.api.client.gui.IMapScreenExtension;
import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public final class SmuScreenExtension implements IMapScreenExtension {

    private final IMapScreenContext context;

    public SmuScreenExtension(IMapScreenContext context) {
        this.context = context;
    }

    @Override
    public void onOpen() {
        this.context.addLayerToggle(
                SmuCompatPlugin.LABEL_LAYER_ID,
                SmuCompatPlugin.LABEL_TOGGLE_SPRITE,
                "x3d_map_compat_smu.layer.exhibitions"
        );
    }
}
