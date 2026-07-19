package com.xkball.x3dmap.api.client.registration;

import com.xkball.x3dmap.api.client.render.MapRenderTarget;
import com.xkball.x3dmap.api.client.render.PictureInPictureRenderLayer;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

import java.util.Set;
import java.util.function.Supplier;

@NonNullByDefault
public interface IMapLayerRegistration {

    void register(Identifier id, Set<MapRenderTarget> targets, Supplier<? extends PictureInPictureRenderLayer<?, ?>> factory);
}
