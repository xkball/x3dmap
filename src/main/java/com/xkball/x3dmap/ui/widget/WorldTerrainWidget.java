package com.xkball.x3dmap.ui.widget;

import com.xkball.x3dmap.ClientConfig;
import com.xkball.x3dmap.ServerConfig;
import com.xkball.x3dmap.api.client.gui.MapWindowRefContainer;
import com.xkball.x3dmap.api.client.gui.MapWindowSpec;
import com.xkball.x3dmap.api.client.storage.IMapDataHandle;
import com.xkball.x3dmap.client.map.gui.MapGuiImpl;
import com.xkball.x3dmap.client.map.gui.MapScreenSession;
import com.xkball.x3dmap.client.map.mapinfo.MapInfoHelper;
import com.xkball.x3dmap.client.map.storage.BuiltinMapDataTypes;
import com.xkball.x3dmap.client.map.uistate.WorldMapUiStateStorage;
import com.xkball.x3dmap.client.terrain.TerrainChunkManager;
import com.xkball.x3dmap.network.c2s.RequestServerChunk;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklib.XKLib;
import com.xkball.xklib.ui.css.property.value.CssLengthUnit;
import com.xkball.xklib.ui.layout.BooleanLayoutVariable;
import com.xkball.xklib.ui.layout.IntLayoutVariable;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.widget.Button;
import com.xkball.xklib.ui.widget.IconButton;
import com.xkball.xklib.ui.widget.IconCheckBox;
import com.xkball.xklib.ui.widget.Label;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklib.ui.widget.container.ContainerWidget;
import com.xkball.xklib.ui.widget.container.WindowedContainer;
import com.xkball.xklibmc.ui.widget.NumberInputWidget;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;

@NonNullByDefault
public class WorldTerrainWidget extends ContainerWidget {
    
    public final BooleanLayoutVariable terrain = new BooleanLayoutVariable(true);
    public final BooleanLayoutVariable grid = new BooleanLayoutVariable(true);
    public final BooleanLayoutVariable player = new BooleanLayoutVariable(true);
    public final BooleanLayoutVariable debug = new BooleanLayoutVariable(false);
    public final BooleanLayoutVariable cameraTarget = new BooleanLayoutVariable(false);
    public final BooleanLayoutVariable compass = new BooleanLayoutVariable(true);
    public final BooleanLayoutVariable depress_sphere = new BooleanLayoutVariable(false);
    public final IntLayoutVariable yMode = new IntLayoutVariable(1);
    public final IntLayoutVariable fixY = new IntLayoutVariable();
    public final IntLayoutVariable lodDistance = new IntLayoutVariable(512);
    public final IntLayoutVariable viewDistance = new IntLayoutVariable(1024);
    public final WorldTerrainWidgetInner inner;
    private final ContainerWidget leftExtensionWidgets = new ContainerWidget();
    private final ContainerWidget top1ExtensionWidgets = new ContainerWidget();
    private final ContainerWidget top2ExtensionWidgets = new ContainerWidget();
    private final WindowedContainer windowLayer;
    private final MapGuiImpl mapGui;
    private @Nullable MapScreenSession screenSession;
    private @Nullable IMapDataHandle<WorldMapUiStateStorage> uiState;
    
    public WorldTerrainWidget(WindowedContainer windowLayer) {
        this.windowLayer = windowLayer;
        var level = Minecraft.getInstance().level;
        var minY = level == null ? -64 : level.getMinY();
        var maxY = level == null ? 384 : level.getMaxY();
        fixY.set(level == null ? 64 : level.getSeaLevel());
        this.lodDistance.set(ClientConfig.WORLD_MAP_LOD_DISTANCE.get());
        this.viewDistance.set(ClientConfig.WORLD_MAP_LOAD_DISTANCE.get());
        TerrainChunkManager.INSTANCE.viewDistance = this.viewDistance.get();
        this.inner = new WorldTerrainWidgetInner(terrain, grid, player, cameraTarget, compass, depress_sphere, debug, yMode, fixY, lodDistance);
        this.mapGui = new MapGuiImpl(this);
        this.initExtensions();
        this.leftExtensionWidgets.inlineStyle("""
                flex-direction: column;
                flex-shrink: 0;
                """);
        this.top1ExtensionWidgets.inlineStyle("flex-direction: row; flex-shrink: 0;");
        this.top2ExtensionWidgets.inlineStyle("flex-direction: row; flex-shrink: 0;");
        this.inlineStyle("""
                        flex-direction: row;
                        size: 100% 100%;
                        """)
                .asRootStyle("""
                        IconCheckBox {
                            size: 14rpx 14rpx;
                            margin-top: 1rpx;
                            margin-left: 1rpx;
                            iconcheckbox-bg-color: 0xAA666666;
                            flex-shrink: 0;
                        }
                        IconButton {
                            size: 14rpx 14rpx;
                            margin-top: 1rpx;
                            margin-left: 1rpx;
                            flex-shrink: 0;
                        }
                        .update_button {
                            size: content 14rpx;
                            margin-top: 1rpx;
                            margin-left: 2rpx;
                            text-align: center;
                            text-scale: expand-width;
                            button-shape: rect;
                            button-bg-color: rgb(229,233,239);
                            text-drop-shadow: false;
                            text-extra-width: 2rpx;
                            text-height: 8rpx;
                        }
                        .property_label {
                            size: content 14rpx;
                            margin-top: 1rpx;
                            margin-left: 2rpx;
                            text-scale: expand-width;
                            text-color: -1;
                            text-height: 8rpx;
                        }
                        .splitter {
                            size: 2px 100%;
                            background-color: 0xEEAAAAAA;
                            margin-left: 2rpx;
                            margin-right: 2rpx;
                            flex-shrink: 0;
                        }
                        NumberInputWidget {
                            size: 50rpx 14rpx;
                            margin-top: 1rpx;
                            margin-left: 1rpx;
                            margin-right: 1rpx;
                            flex-shrink: 0;
                        }
                        """)
                .addChild(this.createToolbarLeft())
                .addChild(new ContainerWidget()
                        .inlineStyle("""
                                flex-direction: column;
                                size: 100%-18rpx 100%;
                                """)
                        .addChild(this.createToolbarTop1(minY, maxY))
                        .addChild(this.createToolbarTop2())
                        .addChild(inner.inlineStyle("height: 100%-35rpx;"))
                );
        this.screenSession = TerrainChunkManager.INSTANCE.mapPluginRegistry.openScreen(this);
        this.inner.setScreenSession(this.screenSession);
    }
    
    public void initExtensions() {
        this.initUiState();
        this.loadPersistentUiState();
        this.bindPersistentUiState();
        this.inner.setUiStateHandle(this.uiState);
    }

    public MapGuiImpl mapGui() {
        return this.mapGui;
    }
    
    public Widget createToolbarLeft() {
        return new ContainerWidget()
                .inlineStyle("""
                        flex-direction: column;
                        size: 18rpx 100%;
                        border-left: 1rpx;
                        border-right: 1rpx;
                        border-color: 0xEEAAAAAA;
                        scrollbar-width: 0;
                        overflow-y: scroll;
                        """)
                .asRootStyle("""
                        .splitter_y{
                                size: 100% 1rpx;
                                background-color: 0xEEAAAAAA;
                        }
                        """)
                .addChild(new Widget().setCSSClassName("splitter_y"))
                .addChild(new IconButton(VanillaUtils.modrl("icon/locate"), inner::reLocateCamera)
                        .inlineStyle("""
                                size: 14rpx 14rpx;
                                margin: 1rpx;
                                flex-shrink: 0;
                                """)
                        .withTooltip(IComponent.translatable("xklibmc.world_terrain.focus_camera")))
                .addChild(new Widget().setCSSClassName("splitter_y"))
                .addChild(new IconCheckBox(VanillaUtils.modrl("icon/map")).bind(terrain).withTooltip(IComponent.translatable("xklibmc.world_terrain.show_terrain")))
                .addChild(new IconCheckBox(VanillaUtils.modrl("icon/compass")).bind(compass).withTooltip(IComponent.translatable("xklibmc.world_terrain.show_compass")))
                .addChild(new IconCheckBox(VanillaUtils.modrl("icon/grid")).bind(grid).withTooltip(IComponent.translatable("xklibmc.world_terrain.show_grid")))
                .addChild(new IconCheckBox(VanillaUtils.modrl("icon/player")).bind(player).withTooltip(IComponent.translatable("xklibmc.world_terrain.show_player")))
                .addChild(new IconCheckBox(VanillaUtils.modrl("icon/locate_camera")).bind(cameraTarget).withTooltip(IComponent.translatable("xklibmc.world_terrain.show_camera_target")))
                .addChild(new IconCheckBox(VanillaUtils.modrl("icon/depress_sphere")).bind(depress_sphere).withTooltip(IComponent.translatable("xklibmc.world_terrain.cull_near_chunks")))
                .addChild(this.leftExtensionWidgets)
                .addChild(new IconCheckBox(VanillaUtils.modrl("icon/debug")).bind(debug).withTooltip(IComponent.translatable("xklibmc.world_terrain.show_debug_info")));
    }
    
    public Widget createToolbarTop1(int minY, int maxY) {
        var toolbar = new ContainerWidget()
                .inlineStyle("""
                        height: 18rpx;
                        border-top: 1rpx;
                        border-bottom: 1rpx;
                        border-color: 0xEEAAAAAA;
                        scrollbar-width: 0;
                        overflow-x: scroll;
                        """)
                .addChild(new IconCheckBox(VanillaUtils.modrl("icon/tracked_y")).bindInGroup(0, yMode).withTooltip(IComponent.translatable("xklibmc.world_terrain.camera_track_terrain")))
                .addChild(new IconCheckBox(VanillaUtils.modrl("icon/fixed_y")).bindInGroup(1, yMode).withTooltip(IComponent.translatable("xklibmc.world_terrain.camera_fixed_y")))
                .addChild(NumberInputWidget.ofInt(minY, maxY, 1).bind(fixY))
                .addChild(new Widget().setCSSClassName("splitter"))
                .addChild(new Label(IComponent.translatable("xklibmc.world_terrain.lod_distance")).setCSSClassName("property_label").withTooltip(IComponent.translatable("xklibmc.world_terrain.in_blocks")))
                .addChild(NumberInputWidget.ofInt(1, 114514, 16).bind(lodDistance))
                .addChild(new Label(IComponent.translatable("xklibmc.world_terrain.load_distance")).setCSSClassName("property_label").withTooltip(IComponent.translatable("xklibmc.world_terrain.in_blocks")))
                .addChild(NumberInputWidget.ofInt(256, 1145141919, 16).bind(viewDistance))
                .addChild(this.top1ExtensionWidgets)
                .addChild(new Button(IComponent.translatable("xklibmc.world_terrain.force_update"), () -> {
                    var player = Minecraft.getInstance().player;
                    var viewDistance = Minecraft.getInstance().options.renderDistance().get();
                    if (player == null) return;
                    TerrainChunkManager.INSTANCE.submitUpdate(player.blockPosition(), viewDistance - 1, true);
                })
                        .setCSSClassName("update_button")
                        .withTooltip(IComponent.translatable("xklibmc.world_terrain.update_chunks"))
                        .inlineStyle("margin-left: auto;"));
        if (ServerConfig.ALLOW_SERVER_SENT_CHUNK.get() || XKLib.IS_DEBUG) {
            toolbar.addChild(new Button(IComponent.translatable("xklibmc.world_terrain.request_geomatics_btn"), () -> {
                var player = Minecraft.getInstance().player;
                if (player == null) return;
                var centerChunk = ChunkPos.containing(player.blockPosition());
                var range = 256;
                var list = new ArrayList<ChunkPos>();
                for (var dx = -range; dx <= range; dx++) {
                    for (var dz = -range; dz <= range; dz++) {
                        var p = new ChunkPos(centerChunk.x() + dx, centerChunk.z() + dz);
                        if (TerrainChunkManager.INSTANCE.getCurrentLevelChunkStorage().containsChunk(p)) continue;
                        list.add(p);
                    }
                }
                ClientPacketDistributor.sendToServer(new RequestServerChunk(list, false));
            }).setCSSClassName("update_button").withTooltip(IComponent.translatable("xklibmc.world_terrain.request_geomatics")));
        }
        toolbar.addChild(new Button(IComponent.translatable("xklibmc.world_terrain.delete"), this::showDeleteConfirmation)
                .setCSSClassName("update_button")
                .inlineStyle("""
                            button-bg-color: rgb(221,0,27);
                            margin-right: 5rpx;
                            text-color: -1;
                        """));
        return toolbar;
    }
    
    private void showDeleteConfirmation() {
        var content = new MapWindowRefContainer();
        content.inlineStyle("flex-direction: column; size: 100% 100%;")
                .addChild(new Label(IComponent.translatable("xklibmc.world_terrain.delete_confirm_msg")).inlineStyle("text-color: 0xFFFF5555; size: 100% 12rpx; text-scale: fit_to_max;"))
                .addChild(new Label(IComponent.translatable("xklibmc.world_terrain.delete_irreversible"))
                        .inlineStyle("text-color: 0xFFFF5555; margin-top: 4px; size: 100% 12rpx; text-scale: fit_to_max;"));
        
        var bottomRow = new ContainerWidget()
                .inlineStyle("flex-direction: row; align-items: center; margin-top: auto;");
        
        var cancelButton = new Button(IComponent.translatable("xklibmc.common.cancel"), content::closeWindow);
        cancelButton.inlineStyle("size: content 12rpx; text-scale: expand-width; text-align: center; margin-left: auto; margin-right: 8px; button-shape: rect; button-bg-color: rgb(75,85,99); text-color: -1; text-drop-shadow: false; text-extra-width: 2rpx; text-height: 10rpx;");
        
        var confirmButton = new Button(IComponent.translatable("xklibmc.world_terrain.delete"), () -> {
            var storage = TerrainChunkManager.INSTANCE.getCurrentLevelChunkStorage();
            if (storage != null) {
                for (var chunk : storage.getChunks()) {
                    storage.deleteChunk(chunk.chunkPos);
                }
            }
            content.closeWindow();
        });
        confirmButton.inlineStyle("size: content 12rpx; text-scale: expand-width; text-align: center; button-shape: rect; button-bg-color: rgb(221,0,27); text-color: -1; text-drop-shadow: false; text-extra-width: 2rpx; text-height: 10rpx;");
        
        bottomRow.addChild(cancelButton);
        bottomRow.addChild(confirmButton);
        content.addChild(bottomRow);
        
        this.mapGui.openWindow(MapWindowSpec.blocking(IComponent.translatable("xklibmc.world_terrain.confirm_delete_title"), false, CssLengthUnit.rpx(180), CssLengthUnit.rpx(120)), content);
    }
    
    public Widget createToolbarTop2() {
        return new ContainerWidget()
                .inlineStyle("""
                        height: 17rpx;
                        border-bottom: 1rpx;
                        border-color: 0xEEAAAAAA;
                        scrollbar-width: 0;
                        overflow-x: scroll;
                        """)
                .addChild(this.top2ExtensionWidgets)
                .addChild(new IconButton(VanillaUtils.modrl("icon/info"), () -> MapInfoHelper.showInfoWindow(this.mapGui))
                        .withTooltip(IComponent.translatable("xklibmc.map_info.button"))
                        .inlineStyle("margin-left: auto;"))
                .addChild(new IconButton(VanillaUtils.modrl("icon/setting"), this::openConfigFile)
                        .withTooltip(IComponent.translatable("xklibmc.world_terrain.open_config"))
                        .inlineStyle("margin-right: 5rpx;"));
    }
    
    private void openConfigFile() {
        var configFile = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("xklibmc_example-client.toml").toFile();
        Util.getPlatform().openFile(configFile);
    }
    
    public void addExtensionLeftBarWidget(Widget widget) {
        this.leftExtensionWidgets.addChild(widget);
    }
    
    public void addExtensionTopBar1Widget(Widget widget) {
        this.top1ExtensionWidgets.addChild(widget);
    }
    
    public void addExtensionTopBar2Widget(Widget widget) {
        this.top2ExtensionWidgets.addChild(widget);
    }
    
    public WindowedContainer.SubWindow addMapSubWindow(Widget content, CssLengthUnit width, CssLengthUnit height) {
        var w = width.resolve(this.windowLayer.getWidth());
        var h = height.resolve(this.windowLayer.getHeight());
        return this.windowLayer.addSubWindow(content, w, h);
    }
    
    public WindowedContainer.SubWindow addMapSubWindow(Widget content, float x, float y, CssLengthUnit width, CssLengthUnit height) {
        var w = width.resolve(this.windowLayer.getWidth());
        var h = height.resolve(this.windowLayer.getHeight());
        return this.windowLayer.addSubWindow(content, x, y, w, h);
    }
    
    public WindowedContainer.SubWindow addMapSubWindow(Widget content, IComponent title, boolean resizable, CssLengthUnit width, CssLengthUnit height) {
        var w = width.resolve(this.windowLayer.getWidth());
        var h = height.resolve(this.windowLayer.getHeight());
        return this.windowLayer.addSubWindow(content, title, resizable, w, h);
    }
    
    public WindowedContainer.SubWindow addMapSubWindow(Widget content, String title, boolean resizable, CssLengthUnit width, CssLengthUnit height) {
        var w = width.resolve(this.windowLayer.getWidth());
        var h = height.resolve(this.windowLayer.getHeight());
        return this.windowLayer.addSubWindow(content, title, resizable, w, h);
    }
    
    public WindowedContainer.SubWindow addMapSubWindow(Widget content, IComponent title, boolean resizable, float x, float y, CssLengthUnit width, CssLengthUnit height) {
        var w = width.resolve(this.windowLayer.getWidth());
        var h = height.resolve(this.windowLayer.getHeight());
        return this.windowLayer.addSubWindow(content, title, resizable, x, y, w, h);
    }
    
    public WindowedContainer.SubWindow addMapSubWindow(Widget content, String title, boolean resizable, float x, float y, CssLengthUnit width, CssLengthUnit height) {
        var w = width.resolve(this.windowLayer.getWidth());
        var h = height.resolve(this.windowLayer.getHeight());
        return this.windowLayer.addSubWindow(content, title, resizable, x, y, w, h);
    }
    
    public WindowedContainer windowLayer() {
        return this.windowLayer;
    }
    
    private void loadPersistentUiState() {
        this.terrain.set(this.getBooleanState("terrain", this.terrain.get()));
        this.grid.set(this.getBooleanState("grid", this.grid.get()));
        this.player.set(this.getBooleanState("player", this.player.get()));
        this.debug.set(this.getBooleanState("debug", this.debug.get()));
        this.cameraTarget.set(this.getBooleanState("camera_target", this.cameraTarget.get()));
        this.compass.set(this.getBooleanState("compass", this.compass.get()));
        this.depress_sphere.set(this.getBooleanState("depress_sphere", this.depress_sphere.get()));
        this.yMode.set(this.getIntState("y_mode", this.yMode.get()));
        this.fixY.set(this.getIntState("fix_y", this.fixY.get()));
    }
    
    private void bindPersistentUiState() {
        this.terrain.addCallback(value -> this.setBooleanState("terrain", value));
        this.grid.addCallback(value -> this.setBooleanState("grid", value));
        this.player.addCallback(value -> this.setBooleanState("player", value));
        this.debug.addCallback(value -> this.setBooleanState("debug", value));
        this.cameraTarget.addCallback(value -> this.setBooleanState("camera_target", value));
        this.compass.addCallback(value -> this.setBooleanState("compass", value));
        this.depress_sphere.addCallback(value -> this.setBooleanState("depress_sphere", value));
        this.yMode.addCallback(value -> this.setIntState("y_mode", value));
        this.fixY.addCallback(value -> this.setIntState("fix_y", value));
        this.lodDistance.addCallback(ClientConfig.WORLD_MAP_LOD_DISTANCE::set);
        this.viewDistance.addCallback(value -> {
            ClientConfig.WORLD_MAP_LOAD_DISTANCE.set(value);
            TerrainChunkManager.INSTANCE.viewDistance = value;
        });
    }
    
    public void closeMap() {
        this.inner.saveUiState();
        if (this.screenSession != null) {
            TerrainChunkManager.INSTANCE.mapPluginRegistry.closeScreen(this.screenSession);
            this.screenSession = null;
        }
        ClientConfig.SPEC.save();
    }

    private void initUiState() {
        var access = TerrainChunkManager.INSTANCE.mapPluginRegistry.runtime().storage().currentLevelData();
        if (access.isPresent()) {
            this.uiState = access.get().get(BuiltinMapDataTypes.UI_STATE);
        }
    }

    private boolean getBooleanState(String key, boolean defaultValue) {
        return this.uiState == null ? defaultValue : this.uiState.value().getBoolean(key, defaultValue);
    }

    private int getIntState(String key, int defaultValue) {
        return this.uiState == null ? defaultValue : this.uiState.value().getInt(key, defaultValue);
    }

    private void setBooleanState(String key, boolean value) {
        if (this.uiState != null) {
            this.uiState.value().setBoolean(key, value);
        }
    }

    private void setIntState(String key, int value) {
        if (this.uiState != null) {
            this.uiState.value().setInt(key, value);
        }
    }
    
}
