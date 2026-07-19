package com.xkball.x3dmap.client.map.mapinfo;

import com.xkball.x3dmap.ClientConfig;
import com.xkball.x3dmap.api.client.gui.IMapGui;
import com.xkball.x3dmap.api.client.gui.MapWindowRefContainer;
import com.xkball.x3dmap.api.client.gui.MapWindowSpec;
import com.xkball.xklib.ui.css.property.value.CssLengthUnit;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.system.GuiSystem;
import com.xkball.xklib.ui.widget.Button;
import com.xkball.xklib.ui.widget.Label;
import com.xkball.xklib.ui.widget.container.ContainerWidget;
import com.xkball.xklibmc.annotation.NonNullByDefault;

import java.util.List;

@NonNullByDefault
public class MapInfoHelper {
    
    private static final List<String> INFO_LINES = List.of(
            "xklibmc.map_info.line.0",
            "xklibmc.map_info.line.1",
            "xklibmc.map_info.line.2",
            "xklibmc.map_info.line.3",
            "xklibmc.map_info.line.4",
            "xklibmc.map_info.line.5",
            "xklibmc.map_info.line.6",
            "xklibmc.map_info.line.7",
            "xklibmc.map_info.line.8"
    );
    
    public static void showInfoIfNeeded(IMapGui mapGui) {
        if (!ClientConfig.SHOW_MAP_INFO.get()) {
            return;
        }
        showInfoWindow(mapGui);
        ClientConfig.SHOW_MAP_INFO.set(false);
        ClientConfig.SPEC.save();
    }
    
    public static void showInfoWindow(IMapGui mapGui) {
        var content = new MapWindowRefContainer();
        content.inlineStyle("flex-direction: column; size: 100% 100%;")
                .asRootStyle("""
                        Label{
                            size: 100% 12rpx;
                            text-height: 10rpx;
                            text-align: left;
                            text-color: -1;
                        }
                        """);
        
        for (var key : INFO_LINES) {
            content.addChild(new Label(IComponent.translatable(key)));
        }
        
        var bottomRow = new ContainerWidget()
                .inlineStyle("size: 100% 18rpx; flex-direction: row; align-items: center;")
                .addChild(new Button(IComponent.translatable("xklibmc.compatibility.ok"), content::closeWindow)
                        .inlineStyle("size: 40rpx 16rpx; margin-left: auto; margin-right: auto; text-scale: expand-width; text-align: center; button-shape: rect;"));
        
        content.addChild(bottomRow);
        
        var gui = GuiSystem.INSTANCE.get();
        var x = Math.max(0f, (gui.screenWidth - 360 * CssLengthUnit.rpxScaleWorkaround) / 2f);
        var y = Math.max(0f, (gui.screenHeight - 240) / 2f);
        mapGui.openWindow(MapWindowSpec.blocking(IComponent.translatable("xklibmc.map_info.title"), false, x, y, CssLengthUnit.rpx(360), CssLengthUnit.rpx(160)), content);
    }
}
