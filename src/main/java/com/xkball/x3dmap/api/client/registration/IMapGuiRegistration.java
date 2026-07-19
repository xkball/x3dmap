package com.xkball.x3dmap.api.client.registration;

import com.xkball.x3dmap.api.client.gui.IMapScreenExtensionFactory;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

@NonNullByDefault
public interface IMapGuiRegistration {

    void addScreenExtension(Identifier id, int order, IMapScreenExtensionFactory factory);
}
