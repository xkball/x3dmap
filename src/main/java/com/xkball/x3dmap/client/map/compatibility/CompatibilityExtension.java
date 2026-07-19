package com.xkball.x3dmap.client.map.compatibility;

import com.xkball.x3dmap.ClientConfig;
import com.xkball.x3dmap.api.client.gui.IMapGui;
import com.xkball.x3dmap.api.client.gui.IMapWindow;
import com.xkball.x3dmap.api.client.gui.MapWindowSpec;
import com.xkball.x3dmap.client.terrain.TerrainChunkManager;
import com.xkball.xklib.ui.css.property.value.CssLengthUnit;
import com.xkball.xklib.ui.layout.BooleanLayoutVariable;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.system.GuiSystem;
import com.xkball.xklib.ui.widget.Button;
import com.xkball.xklib.ui.widget.CheckBox;
import com.xkball.xklib.ui.widget.Label;
import com.xkball.xklib.ui.widget.container.ContainerWidget;
import com.xkball.xklibmc.annotation.NonNullByDefault;

import java.util.ArrayList;

@NonNullByDefault
public class CompatibilityExtension {
    
    public static void initCompatibilityMode() {
        var reasons = new ArrayList<String>();
        
        if (ClientConfig.FORCE_COMPATIBILITY_MODE.get()) {
            reasons.add("Config: forceCompatibilityMode is enabled");
        }
        
        var missingExtensions = GLCompatibilityChecker.checkMissingExtensions();
        for (var ext : missingExtensions) {
            reasons.add("Missing GL extension: " + ext);
        }
        
        if (!reasons.isEmpty()) {
            TerrainChunkManager.INSTANCE.compatibleMode = true;
            TerrainChunkManager.INSTANCE.compatibilityReasons = reasons;
        }
    }
    
    public static void showWarningIfNeeded(IMapGui mapGui) {
        if (!TerrainChunkManager.INSTANCE.compatibleMode) {
            return;
        }
        if (TerrainChunkManager.INSTANCE.compatibilityWarningSuppressed) {
            return;
        }
        
        var dontShowAgain = new BooleanLayoutVariable(false);
        
        var content = new ContainerWidget()
                .inlineStyle("flex-direction: column; size: 100% 100%;")
                .asRootStyle("""
                        Label{
                            size: 100% 12rpx;
                            text-height: 10rpx;
                            text-align: left;
                            text-color: -1;
                        }
                        """)
                .addChild(new Label(IComponent.translatable("xklibmc.compatibility.mode_active")))
                .addChild(new Label(IComponent.translatable("xklibmc.compatibility.warn")))
                .addChild(new Label(IComponent.translatable("xklibmc.compatibility.reasons")));
        
        for (var reason : TerrainChunkManager.INSTANCE.compatibilityReasons) {
            content.addChild(new Label("  - " + reason));
        }
        
        var subWindowRef = new IMapWindow[1];
        var bottomRow = new ContainerWidget()
                .inlineStyle("size: 100% 18rpx; flex-direction: row; align-items: center;")
                .addChild(new CheckBox()
                        .bind(dontShowAgain)
                        .inlineStyle("size: 36rpx 14rpx; margin-left: auto;"))
                .addChild(new Label(IComponent.translatable("xklibmc.compatibility.dont_show_again"))
                        .inlineStyle("margin-left: 4rpx; text-scale: expand-width;"))
                .addChild(new Button(IComponent.translatable("xklibmc.compatibility.ok"), () -> {
                    if (dontShowAgain.get()) {
                        TerrainChunkManager.INSTANCE.compatibilityWarningSuppressed = true;
                    }
                    if (subWindowRef[0] != null) {
                        subWindowRef[0].close();
                    }
                }).inlineStyle("size: 40rpx 16rpx; text-scale: expand-width; text-align: center; button-shape: rect;"));
        content.addChild(bottomRow);
        
        var gui = GuiSystem.INSTANCE.get();
        var x = Math.max(0f, (gui.screenWidth - 240 * CssLengthUnit.rpxScaleWorkaround) / 2f);
        var y = Math.max(0f, (gui.screenHeight - 120) / 2f);
        subWindowRef[0] = mapGui.openWindow(MapWindowSpec.blocking(IComponent.translatable("xklibmc.compatibility.warning_title"), false, x, y, CssLengthUnit.rpx(240), CssLengthUnit.rpx(240)), content);
    }
}
