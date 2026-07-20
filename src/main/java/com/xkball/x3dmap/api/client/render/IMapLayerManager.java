package com.xkball.x3dmap.api.client.render;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;
import java.util.List;

@NonNullByDefault
public interface IMapLayerManager {

    List<Map3dLayerSpec> threeDimensionalLayers(Identifier preset);

    List<Map2dLayerSpec> twoDimensionalLayers(Identifier preset);
}
