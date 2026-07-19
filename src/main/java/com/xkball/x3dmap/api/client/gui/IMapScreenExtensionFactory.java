package com.xkball.x3dmap.api.client.gui;

import com.xkball.xklibmc.annotation.NonNullByDefault;

@FunctionalInterface
@NonNullByDefault
public interface IMapScreenExtensionFactory {

    IMapScreenExtension create(IMapScreenContext context);
}
