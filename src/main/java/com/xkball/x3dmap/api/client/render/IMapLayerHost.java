package com.xkball.x3dmap.api.client.render;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

import java.util.List;

@NonNullByDefault
public interface IMapLayerHost {

    IMapLayerHandle add3d(Map3dLayerSpec spec, IMap3dLayerFactory factory);

    IMapLayerHandle add2d(Map2dLayerSpec spec, IMap2dLayerFactory factory);

    List<Identifier> threeDimensionalLayers();

    List<Identifier> twoDimensionalLayers();

    boolean visible(Identifier id);

    void setVisible(Identifier id, boolean visible);
}
