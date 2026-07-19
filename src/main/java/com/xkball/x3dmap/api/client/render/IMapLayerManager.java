package com.xkball.x3dmap.api.client.render;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

import java.util.List;

@NonNullByDefault
public interface IMapLayerManager {

    List<Identifier> layers(MapRenderTarget target);
}
