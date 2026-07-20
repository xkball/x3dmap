package com.xkball.x3dmap.client.render.pip.layers;

import com.xkball.x3dmap.api.client.render.IMap3dLayer;
import com.xkball.x3dmap.api.client.render.IMap3dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMap3dRenderContext;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.client.Minecraft;

@NonNullByDefault
public class PlayerOnMapRenderer implements IMap3dLayer {

    @Override
    public IMap3dRenderCommand prepareRender(IMapFrame frame) {
        return this::render;
    }

    private void render(IMap3dRenderContext context) {
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
                entityRenderDispatcher.submit(playerState, context.cameraRenderState(), playerPos.x, playerPos.y, playerPos.z, context.poseStack(), featureRenderDispatcher.getSubmitNodeStorage());
            }
        }
        featureRenderDispatcher.renderAllFeatures();
    }
}
