package com.xkball.x3dmap.api.client.registration;

import com.xkball.x3dmap.api.client.render.IMap2dLayerFactory;
import com.xkball.x3dmap.api.client.render.IMap3dLayerFactory;
import com.xkball.x3dmap.api.client.render.Map2dLayerSpec;
import com.xkball.x3dmap.api.client.render.Map3dLayerSpec;
import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public interface IMapLayerRegistration {

    void add3d(Map3dLayerSpec spec, IMap3dLayerFactory factory);

    void add2d(Map2dLayerSpec spec, IMap2dLayerFactory factory);
}
