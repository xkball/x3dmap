package com.xkball.x3dmap.client.render.pip.layers;

import com.xkball.x3dmap.api.client.render.IMap2dLayer;
import com.xkball.x3dmap.api.client.render.IMap2dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMap2dRenderContext;
import com.xkball.x3dmap.api.client.render.IMapFrame;
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
            var entity = level.getEntity(playerInfo.getProfile().id());
            if (entity == null) {
                continue;
            }
            var worldPosition = entity.position().toVector3f().add(0, 2f, 0);
            var screenPosition = context.frame().worldToScreen(worldPosition);
            if (screenPosition == null) {
                continue;
            }
            var x = screenPosition.x - 8;
            var y = screenPosition.y - 10;
            PlayerFaceExtractor.extractRenderState(graphics.getInner(), playerInfo.getSkin(), (int) x, (int) y, 16);
            y -= 10;
            graphics.drawCenteredString(playerInfo.getProfile().name(), screenPosition.x, y, -1);
        }
    }
}
