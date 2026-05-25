package com.xkball.x3dmap.client.b3d;

import com.xkball.xklibmc.client.b3d.uniform.UpdatableUBO;

public class X3dMapUniforms {
    
    public static final UpdatableUBO LEVEL_DATA = new UpdatableUBO.UBOBuilder("level_data")
            .closeOnExit()
            .putFloat("min_height", () -> 0)
            .putFloat("max_height", () -> 0)
            .putFloat("sea_level", () -> 0)
            .putFloat("unused_padding", () -> 0)
            .build();
}
