package com.xkball.x3dmap.api.client.gui;

import com.xkball.x3dmap.api.client.runtime.IX3dMapRuntime;
import com.xkball.x3dmap.api.client.viewport.IMapViewport;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

@NonNullByDefault
public interface IMapScreenContext {

    Identifier extensionId();

    IX3dMapRuntime runtime();

    IMapViewport viewport();

    IMapGui gui();
}
