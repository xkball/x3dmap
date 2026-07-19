package com.xkball.x3dmap.api.client;

import com.xkball.x3dmap.api.client.registration.IMapGuiRegistration;
import com.xkball.x3dmap.api.client.registration.IMapLayerRegistration;
import com.xkball.x3dmap.api.client.registration.IMapStorageRegistration;
import com.xkball.x3dmap.api.client.runtime.IX3dMapRuntime;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

@NonNullByDefault
public interface IX3dMapPlugin {

    Identifier getPluginUid();

    default void registerStorage(IMapStorageRegistration registration) {
    }

    default void registerGui(IMapGuiRegistration registration) {
    }

    default void registerLayers(IMapLayerRegistration registration) {
    }

    default void onRuntimeAvailable(IX3dMapRuntime runtime) {
    }

    default void onRuntimeUnavailable() {
    }
}
