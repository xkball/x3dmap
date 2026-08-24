package com.xkball.x3dmap.client.map.selection;

import com.xkball.x3dmap.api.client.render.IMap3dLayer;
import com.xkball.x3dmap.api.client.render.IMap3dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public class SelectionOverlayRenderer implements IMap3dLayer {

    @Override
    public IMap3dRenderCommand prepareRender(IMapFrame frame) {
        return _ -> {
        };
    }
}
