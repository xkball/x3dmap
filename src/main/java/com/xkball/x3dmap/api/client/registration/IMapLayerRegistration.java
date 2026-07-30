package com.xkball.x3dmap.api.client.registration;

import com.xkball.x3dmap.api.client.render.IMap2dLayerFactory;
import com.xkball.x3dmap.api.client.render.IMap3dLayerFactory;
import com.xkball.x3dmap.api.client.render.IMap3dRenderCommand;
import com.xkball.x3dmap.api.client.render.Map2dLayerSpec;
import com.xkball.x3dmap.api.client.render.Map3dLayerPhase;
import com.xkball.x3dmap.api.client.render.Map3dLayerSpec;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

import java.util.Set;

@NonNullByDefault
public interface IMapLayerRegistration {

    void add3d(Map3dLayerSpec spec, IMap3dLayerFactory factory);

    void add2d(Map2dLayerSpec spec, IMap2dLayerFactory factory);
    
    default void add3d(Identifier id, Set<Identifier> presets, Map3dLayerPhase phase, IMap3dRenderCommand renderer){
        var spec = new Map3dLayerSpec(id, presets, phase, 0, 0, true);
        this.add3d(spec, (_) -> (_) -> renderer);
    }
}
