package com.xkball.x3dmap.client.render.pip.layers;

import com.xkball.x3dmap.api.client.render.IMap2dLayer;
import com.xkball.x3dmap.api.client.render.IMap2dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMap2dRenderContext;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.x3dmap.api.client.render.MapViewportPresets;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.x3d.backend.b3d.B3dGuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerFaceExtractor;

@NonNullByDefault
public final class PlayerHeadsMapLayer implements IMap2dLayer {

    @Override
    public IMap2dRenderCommand extract(IMapFrame frame) {
        return this::render;
    }

    private void render(IMap2dRenderContext context) {
        if (!(context.graphics() instanceof B3dGuiGraphics graphics)) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        var player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        for (var playerInfo : player.connection.getListedOnlinePlayers()) {
            var minimap = context.frame().preset().equals(MapViewportPresets.MINIMAP);
            if (minimap && playerInfo.getProfile().id().equals(player.getUUID())) {
                continue;
            }
            var entity = level.getEntity(playerInfo.getProfile().id());
            if (entity == null) {
                continue;
            }
            var worldPosition = entity.position().toVector3f().add(0, 2f, 0);
            var screenPosition = context.frame().worldToScreen(worldPosition);
            if (screenPosition == null) {
                continue;
            }
            var faceSize = minimap ? 8 : 16;
            var x = screenPosition.x - faceSize / 2.0f;
            var y = screenPosition.y - faceSize / 2.0f;
            PlayerFaceExtractor.extractRenderState(graphics.getInner(), playerInfo.getSkin(), (int) x, (int) y, faceSize);
            var nameY = y - (minimap ? 8 : 10);
            graphics.drawCenteredString(
                    playerInfo.getProfile().name(),
                    screenPosition.x,
                    nameY,
                    -1,
                    minimap ? 6.0f : graphics.defaultFont().lineHeight()
            );
        }
    }
}
