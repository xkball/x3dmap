package com.xkball.x3dmap.client.map.gui;

import com.xkball.x3dmap.api.client.gui.IMapGui;
import com.xkball.x3dmap.api.client.gui.IMapScreenContext;
import com.xkball.x3dmap.api.client.gui.IMapView;
import com.xkball.x3dmap.api.client.runtime.IX3dMapRuntime;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

@NonNullByDefault
public record MapScreenContextImpl(
        Identifier extensionId,
        IX3dMapRuntime runtime,
        IMapView view,
        IMapGui gui
) implements IMapScreenContext {
}
