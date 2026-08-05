package com.xkball.x3dmap.compat.mtr;

import com.xkball.x3dmap.api.client.render.IMap2dLayer;
import com.xkball.x3dmap.api.client.render.IMap2dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMap2dRenderContext;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.x3dmap.api.client.render.MapViewportPresets;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.x3d.backend.b3d.B3dGuiGraphics;
import mtr.client.ClientData;
import mtr.data.IGui;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@NonNullByDefault
public final class MtrStationLayer implements IMap2dLayer {
    
    @Override
    public @Nullable IMap2dRenderCommand extract(IMapFrame frame) {
        var labels = new ArrayList<StationLabel>();
        for (var station : List.copyOf(ClientData.STATIONS)) {
            var center = station.getCenter();
            labels.add(new StationLabel(
                    IGui.textOrUntitled(station.name),
                    new Vector3f(center.getX() + 0.5f, center.getY() + 2.0f, center.getZ() + 0.5f)
            ));
        }
        if (labels.isEmpty()) {
            return null;
        }
        return context -> render(context, List.copyOf(labels));
    }
    
    private static void render(IMap2dRenderContext context, List<StationLabel> labels) {
        if (!(context.graphics() instanceof B3dGuiGraphics graphics)) {
            return;
        }
        var minimap = context.frame().preset().equals(MapViewportPresets.MINIMAP);
        var textHeight = minimap ? 7.0f : 14.0f;
        var padding = minimap ? 1.0f : 2.0f;
        var markerRadius = minimap ? 1.5f : 3.0f;
        for (var label : labels) {
            var screenPosition = context.frame().worldToScreen(label.position());
            if (screenPosition == null) {
                continue;
            }
            var textWidth = graphics.defaultFont().width(label.name(), textHeight);
            var labelWidth = textWidth + padding * 2;
            var labelHeight = textHeight + padding * 2;
            var labelX = screenPosition.x - labelWidth / 2;
            var labelY = screenPosition.y - labelHeight - markerRadius - 2;
            graphics.fillRounded(
                    labelX,
                    labelY,
                    labelX + labelWidth,
                    labelY + labelHeight,
                    0xA0000000,
                    minimap ? 2.0f : 4.0f
            );
            graphics.drawString(label.name(), labelX + padding, labelY + padding, 0xFFFFFFFF, textHeight);
            graphics.fillRounded(
                    screenPosition.x - markerRadius,
                    screenPosition.y - markerRadius,
                    screenPosition.x + markerRadius,
                    screenPosition.y + markerRadius,
                    0xFFFFFFFF,
                    markerRadius
            );
        }
    }
    
    private record StationLabel(String name, Vector3f position) {
    }
}
