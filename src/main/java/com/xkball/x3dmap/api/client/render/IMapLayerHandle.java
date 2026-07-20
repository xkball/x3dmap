package com.xkball.x3dmap.api.client.render;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

@NonNullByDefault
public interface IMapLayerHandle extends AutoCloseable {

    Identifier id();

    boolean visible();

    void setVisible(boolean visible);

    void invalidate();

    @Override
    void close();
}
