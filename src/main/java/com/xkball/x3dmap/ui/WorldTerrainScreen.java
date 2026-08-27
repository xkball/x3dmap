package com.xkball.x3dmap.ui;

import com.xkball.x3dmap.client.map.compatibility.CompatibilityExtension;
import com.xkball.x3dmap.client.map.mapinfo.MapInfoHelper;
import com.xkball.x3dmap.ui.widget.WorldTerrainWidget;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.widget.container.WindowedContainer;
import com.xkball.xklibmc.ui.XKLibBaseScreen;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.network.chat.Component;

@NonNullByDefault
public class WorldTerrainScreen extends XKLibBaseScreen {

    private final WindowedContainer windowLayer;
    private final WorldTerrainWidget worldTerrainWidget;

    public WorldTerrainScreen() {
        super(Component.empty());
        this.windowLayer = new WindowedContainer();
        this.windowLayer.inlineStyle("size: 100% 100%;");
        this.worldTerrainWidget = new WorldTerrainWidget(this.windowLayer, IComponent.translatable("xklibmc.screen.x3d_map"));
        this.addScreenLayer(this.worldTerrainWidget);
        this.addScreenLayer(this.windowLayer);
    }

    @Override
    protected void init() {
        super.init();
        CompatibilityExtension.showWarningIfNeeded(this.worldTerrainWidget.mapGui());
        MapInfoHelper.showInfoIfNeeded(this.worldTerrainWidget.mapGui());
    }
    
    @Override
    public void tick() {
        super.tick();
        worldTerrainWidget.tick();
    }
    
    @Override
    public void removed() {
        this.worldTerrainWidget.closeMap();
        super.removed();
    }

}
