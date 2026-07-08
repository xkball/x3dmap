package com.xkball.x3dmap.client.b3d;

import com.xkball.x3dmap.client.b3d.pipeline.X3dMapRenderPipelines;
import com.xkball.xklibmc.api.client.b3d.SamplerCacheCache;
import com.xkball.xklibmc.client.b3d.postprocess.PostProcess;
import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public class X3dMapPostProcesses {
    
    public static final PostProcess TERRAIN_DEPTH_EDGE = PostProcess.builder()
            .withTexture("input", false, SamplerCacheCache.LINEAR_CLAMP)
            .withTexture("input", true, SamplerCacheCache.LINEAR_CLAMP)
            .applyOnce(X3dMapRenderPipelines.TERRAIN_DEPTH_EDGE, "swap", PostProcess::drawcall)
            .swapBack()
            .build("terrain_depth_edge");
}
