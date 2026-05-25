package com.xkball.x3dmap.client.map.mapinfo;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.ClientConfig;
import com.xkball.x3dmap.api.client.map.WorldMapExtensionService;
import com.xkball.xklib.ui.css.property.value.CssLengthUnit;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.system.GuiSystem;
import com.xkball.xklib.ui.widget.Button;
import com.xkball.xklib.ui.widget.Label;
import com.xkball.xklib.ui.widget.container.ContainerWidget;
import com.xkball.xklib.ui.widget.container.WindowedContainer;
import org.slf4j.Logger;

import java.util.List;

public class MapInfoHelper {
    
    private static final Logger LOGGER = LogUtils.getLogger();
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
    
    public static void showInfoIfNeeded(WorldMapExtensionService service) {
        if (!ClientConfig.SHOW_MAP_INFO.get()) {
            return;
        }
        showInfoWindow(service);
        ClientConfig.SHOW_MAP_INFO.set(false);
        ClientConfig.SPEC.save();
    }
    
    public static void showInfoWindow(WorldMapExtensionService service) {
        var content = new ContainerWidget()
                .inlineStyle("flex-direction: column; size: 100% 100%;")
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
        
        var subWindowRef = new WindowedContainer.SubWindow[1];
        var bottomRow = new ContainerWidget()
                .inlineStyle("size: 100% 18rpx; flex-direction: row; align-items: center;")
                .addChild(new Button(IComponent.translatable("xklibmc.compatibility.ok"), () -> {
                    if (subWindowRef[0] != null) {
                        subWindowRef[0].close();
                    }
                }).inlineStyle("size: 40rpx 16rpx; margin-left: auto; margin-right: auto; text-scale: expand-width; text-align: center; button-shape: rect;"));
        
        content.addChild(bottomRow);
        
        var gui = GuiSystem.INSTANCE.get();
        var x = Math.max(0f, (gui.screenWidth - 360 * CssLengthUnit.rpxScaleWorkaround) / 2f);
        var y = Math.max(0f, (gui.screenHeight - 240) / 2f);
        subWindowRef[0] = service.addBlockingSubWindow(content, IComponent.translatable("xklibmc.map_info.title"), false, x, y, CssLengthUnit.rpx(360), CssLengthUnit.rpx(160));
    }
}
