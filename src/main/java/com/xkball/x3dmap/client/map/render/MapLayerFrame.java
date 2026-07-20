package com.xkball.x3dmap.client.map.render;

import com.xkball.xklibmc.annotation.NonNullByDefault;

import java.util.List;

@NonNullByDefault
public record MapLayerFrame(
        List<PreparedMap3dLayer> threeDimensionalLayers,
        List<PreparedMap2dLayer> twoDimensionalLayers
) {

    public MapLayerFrame {
        threeDimensionalLayers = List.copyOf(threeDimensionalLayers);
        twoDimensionalLayers = List.copyOf(twoDimensionalLayers);
    }
}
