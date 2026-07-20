package com.xkball.x3dmap.client.map.render;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.api.client.render.IMap2dRenderContext;
import com.xkball.x3dmap.api.client.render.Map2dLayerPhase;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.slf4j.Logger;

@NonNullByDefault
public final class Map2dLayerRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private Map2dLayerRenderer() {
    }

    public static void render(MapLayerFrame frame, Map2dLayerPhase phase, IMap2dRenderContext context) {
        for (var layer : frame.twoDimensionalLayers()) {
            if (layer.spec().phase() != phase) {
                continue;
            }
            try {
                layer.command().render(context);
            } catch (Exception e) {
                LOGGER.error("Failed to render 2D map layer {}", layer.spec().id(), e);
            }
        }
    }
}
