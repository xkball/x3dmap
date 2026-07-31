package com.xkball.x3dmap.compat.smu;

import com.xkball.x3dmap.api.client.render.IMap2dLayer;
import com.xkball.x3dmap.api.client.render.IMap2dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMap2dRenderContext;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.xklib.resource.ResourceLocation;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.x3d.backend.b3d.B3dGuiGraphics;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.teacon.exhibition_portal.components.ExhibitionMetadata;

import java.util.List;

@NonNullByDefault
public final class SmuExhibitionLabelLayer implements IMap2dLayer {

    private static final ResourceLocation PINNED_ICON = new ResourceLocation("x3d_map", "icon/pinned");

    @Override
    public @Nullable IMap2dRenderCommand extract(IMapFrame frame) {
        var labels = SmuClientData.labels();
        return labels.isEmpty() ? null : context -> render(context, labels);
    }

    private static void render(IMap2dRenderContext context, List<ExhibitionMetadata> labels) {
        for (var label : labels) {
            renderLabel(context, label);
        }
    }

    private static void renderLabel(IMap2dRenderContext context, ExhibitionMetadata label) {
        var waypoint = label.waypoint();
        var screenPosition = context.frame().worldToScreen(new Vector3f(waypoint.x() + 0.5f, waypoint.y(), waypoint.z() + 0.5f));
        if (screenPosition == null) {
            return;
        }
        var graphics = context.graphics();
        if (!(graphics instanceof B3dGuiGraphics guiGraphics)) {
            return;
        }
        var scale = guiGraphics.scale / 2;
        var x = screenPosition.x;
        var y = screenPosition.y - 16.0f;
        var textWidth = graphics.defaultFont().width(label.name(), 14.0f);
        graphics.fillRounded(x, y, x + (18.0f + textWidth) * scale, y + 16.0f * scale, 0x88000000, 4.0f);
        graphics.blitSprite(PINNED_ICON, x + scale, y + scale, 14.0f * scale, 14.0f * scale, -1);
        graphics.drawString(label.name(), x + 16.0f * scale, y + 2.0f * scale, -1, 14.0f * scale);
    }
}
