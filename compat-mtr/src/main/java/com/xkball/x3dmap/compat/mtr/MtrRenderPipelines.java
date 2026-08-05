package com.xkball.x3dmap.compat.mtr;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.client.b3d.pipeline.ExtendedRenderPipeline;
import com.xkball.xklibmc.client.b3d.uniform.XKLibUniforms;
import com.xkball.xklibmc.x3d.backend.b3d.vertex.B3dVertexFormats;
import net.minecraft.resources.Identifier;

@NonNullByDefault
public final class MtrRenderPipelines {
    
    public static final ExtendedRenderPipeline ROUTE_LINE = ExtendedRenderPipeline.builder()
            .withLocation(X3dMapMtrCompat.id("route_line"))
            .withVertexShader(X3dMapMtrCompat.id("core/route_line"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("xklibmc", "core/pos_color"))
            .withVertexFormat(B3dVertexFormats.LINE, VertexFormat.Mode.TRIANGLES)
            .withUniform("ScreenSize", UniformType.UNIFORM_BUFFER)
            .bindUniform("ScreenSize", XKLibUniforms.SCREEN_SIZE)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .buildExtended();
    
    private MtrRenderPipelines() {
    }
}
