package com.xkball.x3dmap.client.map.gui;

import com.xkball.x3dmap.api.client.gui.IMapGui;
import com.xkball.x3dmap.api.client.gui.IMapScreenContext;
import com.xkball.x3dmap.api.client.runtime.IX3dMapRuntime;
import com.xkball.x3dmap.api.client.viewport.IMapViewport;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

@NonNullByDefault
public record MapScreenContextImpl(
        Identifier extensionId,
        IX3dMapRuntime runtime,
        IMapViewport viewport,
        IMapGui gui
) implements IMapScreenContext {
}
