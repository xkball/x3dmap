package com.xkball.x3dmap.ui;

import com.xkball.x3dmap.client.map.WorldMapExtensionServiceImpl;
import com.xkball.x3dmap.client.map.compatibility.CompatibilityExtension;
import com.xkball.x3dmap.client.map.mapinfo.MapInfoHelper;
import com.xkball.x3dmap.ui.widget.WorldTerrainWidget;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.widget.container.WindowedContainer;
import com.xkball.xklibmc.ui.XKLibBaseScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class WorldTerrainScreen extends XKLibBaseScreen {
    
    private final WindowedContainer windowLayer;
    private final WorldTerrainWidget worldTerrainWidget;
    private final WorldMapExtensionServiceImpl extensionService;
    
    public WorldTerrainScreen() {
        super(Component.empty());
        this.windowLayer = new WindowedContainer();
        this.windowLayer.inlineStyle("size: 100% 100%;");
        this.extensionService = new WorldMapExtensionServiceImpl("");
        this.worldTerrainWidget = new WorldTerrainWidget(this.windowLayer, extensionService);
        this.addScreenLayer(XKLibBaseScreen.frame(IComponent.translatable("xklibmc.screen.x3d_map"), this.worldTerrainWidget));
        this.addScreenLayer(this.windowLayer);
    }
    
    @Override
    protected void init() {
        super.init();
        CompatibilityExtension.showWarningIfNeeded(this.extensionService);
        MapInfoHelper.showInfoIfNeeded(this.extensionService);
    }
    
    @Override
    public void removed() {
        this.worldTerrainWidget.closeMapExtensions();
        super.removed();
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.worldTerrainWidget.inner.tick();
        this.worldTerrainWidget.inner.calculateNewPipState();
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }
}
