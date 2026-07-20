package com.xkball.x3dmap.api.client.viewport;

import com.xkball.x3dmap.api.client.render.IMapLayerHost;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

@NonNullByDefault
public interface IMapViewport extends AutoCloseable {

    ResourceKey<Level> dimension();

    Identifier preset();

    Widget widget();

    IMapCamera camera();

    IMapProjection projection();

    IMapLayerHost layers();

    void invalidate();

    @Override
    void close();
}
