package com.xkball.x3dmap.compat.mtr;

import com.xkball.x3dmap.api.client.gui.IMapScreenContext;
import com.xkball.x3dmap.api.client.gui.IMapScreenExtension;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public final class MtrScreenExtension implements IMapScreenExtension {

    private final IMapScreenContext context;

    public MtrScreenExtension(IMapScreenContext context) {
        this.context = context;
    }

    @Override
    public void onOpen() {
        this.context.addLayerToggle(
                X3dMapMtrCompat.id("transit"),
                VanillaUtils.modRL("icon/route"),
                "x3d_map_compat_mtr.layer.transit",
                MtrCompatPlugin.ROUTE_LAYER,
                MtrCompatPlugin.STATION_LAYER
        );
    }
}
