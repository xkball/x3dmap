package com.xkball.x3dmap.client.render.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xkball.x3dmap.block.entity.TerrainProjectorBlockEntity;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

@NonNullByDefault
public final class TerrainProjectorBlockEntityRenderer implements BlockEntityRenderer<TerrainProjectorBlockEntity, TerrainProjectorBlockEntityRenderer.RenderState> {

    public TerrainProjectorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
    }

    public static final class RenderState extends BlockEntityRenderState {
    }
}
