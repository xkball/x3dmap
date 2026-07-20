package com.xkball.x3dmap.client.render.pip.layers;

import com.xkball.x3dmap.api.client.render.IMap2dLayer;
import com.xkball.x3dmap.api.client.render.IMap2dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMap2dRenderContext;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.x3dmap.client.map.minimap.CompassRenderer;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.x3d.backend.b3d.B3dGuiGraphics;

@NonNullByDefault
public final class CompassMapLayer implements IMap2dLayer {

    @Override
    public IMap2dRenderCommand extract(IMapFrame frame) {
        var yRotation = frame.camera().yRotation();
        return context -> this.render(context, yRotation);
    }

    private void render(IMap2dRenderContext context, float yRotation) {
        if (context.graphics() instanceof B3dGuiGraphics graphics) {
            var frame = context.frame();
            CompassRenderer.render(
                    graphics,
                    frame.viewportX() + 8,
                    frame.viewportY() + 10,
                    frame.viewportX() + frame.viewportWidth(),
                    frame.viewportY() + frame.viewportHeight(),
                    yRotation,
                    6,
                    24f
            );
        }
    }
}
