package com.xkball.x3dmap.client.map.minimap;

import com.xkball.xklib.ui.css.property.value.CssLengthUnit;
import com.xkball.xklib.ui.layout.BooleanLayoutVariable;
import com.xkball.xklib.ui.layout.IntLayoutVariable;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.widget.CheckBox;
import com.xkball.xklib.ui.widget.IconButton;
import com.xkball.xklib.ui.widget.Label;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklib.ui.widget.container.ContainerWidget;
import com.xkball.xklib.ui.widget.container.WindowedContainer;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.x3dmap.ClientConfig;
import com.xkball.x3dmap.api.client.map.WorldMapExtension;
import com.xkball.x3dmap.api.client.map.WorldMapExtensionContext;
import com.xkball.x3dmap.api.client.map.WorldMapExtensionService;
import com.xkball.x3dmap.client.terrain.LevelChunkStorage;
import com.xkball.x3dmap.ui.widget.IntSliderWidget;
import org.jspecify.annotations.Nullable;

public class MinimapExtension implements WorldMapExtension {

    public static final String EXTENSION_ID = "minimap";
    
    public final IntLayoutVariable highDetailRange = new IntLayoutVariable(8);
    public final BooleanLayoutVariable rotateWithPlayer = new BooleanLayoutVariable(false);
    public final BooleanLayoutVariable minimapEnabled = new BooleanLayoutVariable(true);
    private WindowedContainer.@Nullable SubWindow configWindow;
    private final MinimapSettingsStorage settingsStorage = new MinimapSettingsStorage();

    public static @Nullable MinimapExtension INSTANCE;

    @Override
    public String id() {
        return EXTENSION_ID;
    }

    @Override
    public void init(WorldMapExtensionContext context) {
        INSTANCE = this;
    }

    @Override
    public void onStorageLoaded(LevelChunkStorage storage) {
        if (storage.getExtensionStorage(MinimapSettingsStorage.EXTENSION_ID) == null) {
            storage.registerExtensionStorage(this.settingsStorage);
        }
    }

    @Override
    public void onMapOpened(WorldMapExtensionService service) {
        this.bindPersistence(service);
        service.addTopBar2Widget(new IconButton(VanillaUtils.modrl("icon/minimap"), () -> this.openConfig(service))
                .withTooltip(IComponent.translatable("xklibmc.minimap.open_settings")));
        service.addTopBar2Widget(new Widget().setCSSClassName("splitter"));
    }

    @Override
    public void onMapClosed(WorldMapExtensionService service) {
        this.configWindow = null;
        this.highDetailRange.removeCallbacks();
        this.rotateWithPlayer.removeCallbacks();
        this.minimapEnabled.removeCallbacks();
        this.settingsStorage.highDetailRange = this.highDetailRange.get();
        this.settingsStorage.rotateWithPlayer = this.rotateWithPlayer.get();
    }
    

    public int highDetailRange() {
        return highDetailRange.get();
    }

    public boolean rotateWithPlayer() {
        return this.rotateWithPlayer.get();
    }
    

    public IntLayoutVariable highDetailRangeVariable() {
        return highDetailRange;
    }

    public BooleanLayoutVariable rotateWithPlayerVariable() {
        return rotateWithPlayer;
    }

    public float camXRot() {
        return settingsStorage.camXRot;
    }

    public void setCamXRot(float value) {
        this.settingsStorage.camXRot = Math.clamp(value, -89.9f, 89.9f);
        this.settingsStorage.markDirty();
        
    }

    public float camFov() {
        return this.settingsStorage.camFov;
    }

    public void setCamFov(float value) {
        this.settingsStorage.camFov = Math.clamp(value, 5, 90);
        this.settingsStorage.markDirty();
    }

    public float camCameraLength() {
        return this.settingsStorage.camCameraLength;
    }

    public void setCamCameraLength(float value) {
        this.settingsStorage.camCameraLength = Math.max(value, 0);
        this.settingsStorage.markDirty();
    }

    private void openConfig(WorldMapExtensionService service) {
        if (this.configWindow != null && this.configWindow.visible()) return;
        this.configWindow = service.addSubWindow(this.createConfigContent(), IComponent.translatable("xklibmc.minimap.config.title"), false, CssLengthUnit.rpx(140), CssLengthUnit.rpx(240));
    }

    private Widget createConfigContent() {
        var preview = new MinimapPreviewWidget(highDetailRange, rotateWithPlayer)
                .inlineStyle("size: 116rpx 116rpx; flex-shrink: 0; margin: 5rpx;");
        return new ContainerWidget() {
            @Override
            public void onRemove() {
                super.onRemove();
                configWindow = null;
            }
        }
                .inlineStyle("""
                        flex-direction: column;
                        size: 100% 100%;
                        padding: 6rpx;
                        scrollbar-width: 0;
                        overflow-y: scroll;
                        """)
                .asRootStyle("""
                        .minimap_row {
                            height: 12rpx;
                            flex-direction: row;
                            align-items: center;
                            margin-top: 2rpx;
                            flex-shrink: 0;
                        }
                        .minimap_label {
                            size: 50% 12rpx;
                            text-color: -1;
                            text-scale: expand-width;
                            flex-shrink: 0;
                        }
                        CheckBox {
                            size: 24rpx 12rpx;
                            flex-shrink: 0;
                            margin-left: auto;
                            margin-right: 2rpx;
                        }
                        """)
                .addChild(preview)
                .addChild(new ContainerWidget()
                        .setCSSClassName("minimap_row")
                        .addChild(new Label(IComponent.translatable("xklibmc.minimap.config.enable")).setCSSClassName("minimap_label"))
                        .addChild(new CheckBox().bind(this.minimapEnabled)))
                .addChild(this.sliderRow(IComponent.translatable("xklibmc.minimap.config.high_detail"), this.highDetailRange))
                .addChild(new ContainerWidget()
                        .setCSSClassName("minimap_row")
                        .addChild(new Label(IComponent.translatable("xklibmc.minimap.config.rotate")).setCSSClassName("minimap_label"))
                        .addChild(new CheckBox().bind(this.rotateWithPlayer)));
    }

    private Widget sliderRow(IComponent label, IntLayoutVariable variable) {
        return new ContainerWidget()
                .setCSSClassName("minimap_row")
                .addChild(new Label(label).setCSSClassName("minimap_label"))
                .addChild(new IntSliderWidget(0, 64, variable.get()).bind(variable).inlineStyle("size: 50% 12rpx;margin-right: 2rpx;"));
    }

    private void bindPersistence(WorldMapExtensionService service) {
        this.highDetailRange.addCallback(value -> {
                settingsStorage.highDetailRange = value;
                settingsStorage.markDirty();
        });
        this.rotateWithPlayer.addCallback(value -> {
                settingsStorage.rotateWithPlayer = value;
                settingsStorage.markDirty();
        });
        this.minimapEnabled.addCallback(ClientConfig.MINIMAP_ENABLED::set);
    }
}
