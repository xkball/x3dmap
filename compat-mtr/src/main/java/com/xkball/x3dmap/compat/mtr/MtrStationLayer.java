package com.xkball.x3dmap.compat.mtr;

import com.xkball.x3dmap.api.client.render.IMap2dLayer;
import com.xkball.x3dmap.api.client.render.IMap2dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMap2dRenderContext;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.x3dmap.api.client.render.IMapLayerContext;
import com.xkball.x3dmap.api.client.render.MapViewportPresets;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklib.resource.ResourceLocation;
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

    private static final ResourceLocation ROUTE_ICON = VanillaUtils.modrl("icon/route");
    private final IMapLayerContext layerContext;

    public MtrStationLayer(IMapLayerContext layerContext) {
        this.layerContext = layerContext;
    }
    
    @Override
    public @Nullable IMap2dRenderCommand extract(IMapFrame frame) {
        var labels = new ArrayList<StationLabel>();
        for (var station : ClientData.STATIONS) {
            if (!ClientData.DATA_CACHE.stationIdToRoutes.containsKey(station.id)) {
                continue;
            }
            var center = station.getCenter();
            labels.add(new StationLabel(
                    IGui.textOrUntitled(station.name),
                    new Vector3f(center.getX() + 0.5f, center.getY() + 2.0f, center.getZ() + 0.5f)
            ));
        }
        if (labels.isEmpty()) {
            return null;
        }
        return context -> {
            if (MtrCompatPlugin.transitVisible(this.layerContext, frame)) {
                render(context, List.copyOf(labels));
            }
        };
    }
    
    private static void render(IMap2dRenderContext context, List<StationLabel> labels) {
        if (!(context.graphics() instanceof B3dGuiGraphics graphics)) {
            return;
        }
        var minimap = context.frame().preset().equals(MapViewportPresets.MINIMAP);
        var scale = minimap ? 1.0f : graphics.scale / 2;
        var textHeight = (minimap ? 7.0f : 14.0f) * scale;
        var iconSize = (minimap ? 7.0f : 14.0f) * scale;
        var labelHeight = (minimap ? 9.0f : 16.0f) * scale;
        var textOffsetX = (minimap ? 9.0f : 16.0f) * scale;
        for (var label : labels) {
            var screenPosition = context.frame().worldToScreen(label.position());
            if (screenPosition == null) {
                continue;
            }
            var textWidth = graphics.defaultFont().width(label.name(), textHeight);
            var labelWidth = textOffsetX + textWidth + (minimap ? 1.0f : 2.0f) * scale;
            var labelX = screenPosition.x;
            var labelY = screenPosition.y - (minimap ? 9.0f : 16.0f);
            graphics.fillRounded(
                    labelX,
                    labelY,
                    labelX + labelWidth,
                    labelY + labelHeight,
                    minimap ? 0xA0000000 : 0x88000000,
                    minimap ? 2.0f : 4.0f
            );
            graphics.blitSprite(ROUTE_ICON, labelX + scale, labelY + scale, iconSize, iconSize, 0xFFFFFFFF);
            graphics.drawString(label.name(), labelX + textOffsetX, labelY + (minimap ? 1.0f : 2.0f) * scale, 0xFFFFFFFF, textHeight);
        }
    }
    
    private record StationLabel(String name, Vector3f position) {
    }
}
