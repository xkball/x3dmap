package com.xkball.x3dmap.client.render.pip.layers;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.xkball.x3dmap.api.client.render.PictureInPictureRenderLayer;
import com.xkball.x3dmap.client.render.pip.WorldTerrainPipRenderer;
import net.minecraft.client.Minecraft;

public class PlayerOnMapRenderer implements PictureInPictureRenderLayer<WorldTerrainPipRenderer, WorldTerrainPipRenderer.WorldTerrainState> {
    
    @Override
    public String name() {
        return "player";
    }
    
    @Override
    public void render(WorldTerrainPipRenderer pip, WorldTerrainPipRenderer.WorldTerrainState renderState, PoseStack poseStack, GpuTextureView texture, GpuTextureView depth) {
        var featureRenderDispatcher = Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher();
        var entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        var player = Minecraft.getInstance().player;
        var level = Minecraft.getInstance().level;
        if (player != null && level != null) {
            var playerInfo = player.connection.getListedOnlinePlayers();
            for (var p : playerInfo) {
                var uuid = p.getProfile().id();
                var entity = Minecraft.getInstance().level.getEntity(uuid);
                if (entity == null) continue;
                var playerState = entityRenderDispatcher.extractEntity(entity, 0);
                var playerPos = entity.position();
                entityRenderDispatcher.submit(playerState, pip.cameraRenderState, playerPos.x, playerPos.y, playerPos.z, poseStack, featureRenderDispatcher.getSubmitNodeStorage());
            }
        }
        featureRenderDispatcher.renderAllFeatures();
    }
}
