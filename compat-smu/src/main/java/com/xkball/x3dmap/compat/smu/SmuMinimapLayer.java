package com.xkball.x3dmap.compat.smu;

import com.xkball.x3dmap.api.client.render.IMap2dLayer;
import com.xkball.x3dmap.api.client.render.IMap2dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMap2dRenderContext;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklib.resource.ResourceLocation;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.x3d.backend.b3d.B3dGuiGraphics;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.teacon.exhibition_portal.components.ExhibitionMetadata;

import java.util.List;

@NonNullByDefault
public final class SmuMinimapLayer implements IMap2dLayer {

    private static final ResourceLocation ICON = VanillaUtils.modrl("icon/museum");

    @Override
    public @Nullable IMap2dRenderCommand extract(IMapFrame frame) {
        var labels = List.copyOf(SmuClientData.labels());
        if (labels.isEmpty()) {
            return null;
        }
        return context -> render(context, labels);
    }

    private static void render(IMap2dRenderContext context, List<ExhibitionMetadata> labels) {
        if (!(context.graphics() instanceof B3dGuiGraphics graphics)) {
            return;
        }
        for (var metadata : labels) {
            var waypoint = metadata.waypoint();
            var screen = context.frame().worldToScreen(new Vector3f(waypoint.x() + 0.5f, waypoint.y() + 1.0f, waypoint.z() + 0.5f));
            if (screen == null) {
                continue;
            }
            var textHeight = 7.0f;
            var iconSize = 7.0f;
            var textWidth = graphics.defaultFont().width(metadata.name(), textHeight);
            var width = iconSize + textWidth + 3;
            var x = screen.x - width / 2;
            var y = screen.y - 11;
            graphics.fillRounded(x, y, x + width, y + 9, 0xA0000000, 2);
            graphics.blitSprite(ICON, x + 1, y + 1, iconSize, iconSize, 0xFFFFFFFF);
            graphics.drawString(metadata.name(), x + iconSize + 2, y + 1, 0xFFFFFFFF, textHeight);
        }
    }
}
