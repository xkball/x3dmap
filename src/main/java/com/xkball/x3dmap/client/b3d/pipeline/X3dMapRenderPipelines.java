package com.xkball.x3dmap.client.b3d.pipeline;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.xkball.x3dmap.client.b3d.X3dMapUniforms;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.client.b3d.pipeline.ExtendedRenderPipeline;
import com.xkball.xklibmc.client.b3d.uniform.UpdatableUBO;
import com.xkball.xklibmc.client.b3d.uniform.XKLibUniforms;
import org.joml.Vector3f;

public class X3dMapRenderPipelines {
    
    public static final UpdatableUBO PHONE_LIGHT = new UpdatableUBO.UBOBuilder("PhongLight")
            .closeOnExit()
            .putVec3("lightPos", Vector3f::new)
            .putVec3("viewPos", Vector3f::new)
            .build();
    
    public static final ExtendedRenderPipeline WORLD_TERRAIN_PIP = ExtendedRenderPipeline.builder()
            .withLocation(VanillaUtils.modRL("world_terrain_pip"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.TRIANGLES)
            .withVertexShader(VanillaUtils.modRL("core/world_terrain_pip"))
            .withFragmentShader(VanillaUtils.modRL("core/world_terrain_pip"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("PhongLight", UniformType.UNIFORM_BUFFER)
            .bindUniform("PhongLight", PHONE_LIGHT)
            .withSSBO("ABlock")
            .withSSBO("FaceIndex")
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(true)
            .buildExtended();
    
    public static final ExtendedRenderPipeline WORLD_TERRAIN_PIP_FULL_MESH = ExtendedRenderPipeline.builder()
            .withLocation(VanillaUtils.modRL("world_terrain_pip_full_mesh"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.TRIANGLES)
            .withVertexShader(VanillaUtils.modRL("core/world_terrain_pip_full_mesh"))
            .withFragmentShader(VanillaUtils.modRL("core/world_terrain_pip_full_mesh"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("PhongLight", UniformType.UNIFORM_BUFFER)
            .bindUniform("PhongLight", PHONE_LIGHT)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(true)
            .buildExtended();
    
    public static final ExtendedRenderPipeline WORLD_TERRAIN_PIP_LOD = ExtendedRenderPipeline.builder()
            .withLocation(VanillaUtils.modRL("world_terrain_pip_lod"))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
            .withVertexShader(VanillaUtils.modRL("core/world_terrain_pip_lod"))
            .withFragmentShader(VanillaUtils.modRL("core/world_terrain_pip_full_mesh"))
            .withSampler("colorTexture")
            .withSampler("heightTexture")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("PhongLight", UniformType.UNIFORM_BUFFER)
            .bindUniform("PhongLight", PHONE_LIGHT)
            .withUniform("LevelData", UniformType.UNIFORM_BUFFER)
            .bindUniform("LevelData", X3dMapUniforms.LEVEL_DATA)
            .withSSBO("cmd")
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(true)
            .buildExtended();
    
    public static final ExtendedRenderPipeline TERRAIN_DEPTH_EDGE = ExtendedRenderPipeline.builder()
            .withLocation(VanillaUtils.modRL("terrain_depth_edge"))
            .withVertexShader(VanillaUtils.resourceLocationOf("xklibmc", "core/blit"))
            .withFragmentShader(VanillaUtils.modRL("core/terrain_depth_edge"))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .withSampler("input0")
            .withSampler("input1")
            .withUniform("ScreenSize", UniformType.UNIFORM_BUFFER)
            .bindUniform("ScreenSize", XKLibUniforms.SCREEN_SIZE)
            .withUniform("InvProjMat", UniformType.UNIFORM_BUFFER)
            .bindUniform("InvProjMat", XKLibUniforms.INVERSE_PROJ_MAT)
            .buildExtended();
    
    public static final ExtendedRenderPipeline SELECTION_OVERLAY = ExtendedRenderPipeline.builder()
            .withLocation(VanillaUtils.modRL("selection_overlay"))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
            .withVertexShader(VanillaUtils.modRL("core/selection_overlay"))
            .withFragmentShader(VanillaUtils.modRL("core/selection_overlay"))
            .withSampler("heightTexture")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("LevelData", UniformType.UNIFORM_BUFFER)
            .bindUniform("LevelData", X3dMapUniforms.LEVEL_DATA)
            .withSSBO("cmd")
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .buildExtended();
    
}
