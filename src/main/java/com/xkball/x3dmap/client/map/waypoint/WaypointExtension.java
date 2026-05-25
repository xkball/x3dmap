package com.xkball.x3dmap.client.map.waypoint;

import com.xkball.xklib.ui.css.property.value.CssLengthUnit;
import com.xkball.xklib.ui.layout.BooleanLayoutVariable;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.widget.IconButton;
import com.xkball.xklib.ui.widget.IconCheckBox;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklib.ui.widget.container.WindowedContainer;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.x3dmap.api.client.map.WorldMapEvent;
import com.xkball.x3dmap.api.client.map.WorldMapExtension;
import com.xkball.x3dmap.api.client.map.WorldMapExtensionService;
import com.xkball.x3dmap.client.terrain.LevelChunkStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public class WaypointExtension implements WorldMapExtension {

    private final BooleanLayoutVariable visible = new BooleanLayoutVariable(true);
    private final WaypointStorage emptyStorage = new WaypointStorage();
    private @Nullable Waypoint temporaryWaypoint;
    private WindowedContainer.@Nullable SubWindow managerWindow;
    private WindowedContainer.@Nullable SubWindow detailWindow;
    private @Nullable UUID detailWaypointId;
    private boolean addingWaypoint;

    @Override
    public String id() {
        return WaypointStorage.EXTENSION_ID;
    }

    @Override
    public void onStorageLoaded(LevelChunkStorage storage) {
        if (storage.getExtensionStorage(WaypointStorage.EXTENSION_ID) == null) {
            storage.registerExtensionStorage(new WaypointStorage());
        }
    }

    @Override
    public void onStorageClosed(@Nullable LevelChunkStorage storage) {
        this.temporaryWaypoint = null;
    }

    @Override
    public void onMapOpened(WorldMapExtensionService service) {
        this.visible.set(service.getBooleanState("visible", this.visible.get()));
        this.visible.addCallback(_ -> service.refreshInnerOverlay());
        this.visible.addCallback(value -> service.setBooleanState("visible", value));
        service.addLeftBarWidget(new IconCheckBox(VanillaUtils.modrl("icon/waypoint")).bind(this.visible).withTooltip(IComponent.translatable("xklibmc.waypoint.show_waypoints")));
        service.addTopBar2Widget(new IconButton(VanillaUtils.modrl("icon/add_waypoint"), () -> this.addingWaypoint = true).withTooltip(IComponent.translatable("xklibmc.waypoint.add_waypoint")));
        service.addTopBar2Widget(new IconButton(VanillaUtils.modrl("icon/manage_waypoint"), () -> this.openManager(service)).withTooltip(IComponent.translatable("xklibmc.waypoint.open_manager")));
        service.addTopBar2Widget(new Widget().setCSSClassName("splitter"));
        service.setInnerOverlayProvider(() -> this.createOverlay(service));
        service.refreshInnerOverlay();
    }
    
    @Override
    public void onMapClosed(WorldMapExtensionService service) {
        this.managerWindow = null;
        this.detailWindow = null;
        this.detailWaypointId = null;
    }
    
    @Override
    public void onMapEvent(WorldMapExtensionService service, WorldMapEvent event) {
        if (!(event instanceof WorldMapEvent.MouseClicked clicked) || clicked.event().button() != 0) {
            return;
        }
        if (this.addingWaypoint) {
            var worldPos = service.projScreen2World(clicked.event().x(), clicked.event().y());
            if (worldPos == null) {
                return;
            }
            var pos = new BlockPos((int) Math.floor(worldPos.x), (int) Math.floor(worldPos.y), (int) Math.floor(worldPos.z));
            this.temporaryWaypoint = new Waypoint(UUID.randomUUID(), Component.translatable("xklibmc.waypoint.default_name").getString(), pos, 0xFF66CCFF, false);
            this.openDetail(service, this.temporaryWaypoint, true, clicked.event().x(), clicked.event().y());
            service.refreshInnerOverlay();
            this.addingWaypoint = false;
            clicked.consume();
            return;
        }
        if (!clicked.doubleClick()) {
            return;
        }
        var worldPos = service.projScreen2World(clicked.event().x(), clicked.event().y());
        if (worldPos == null) {
            return;
        }
        if (this.isDetailWindowOpen()) {
            clicked.consume();
            return;
        }
        var pos = new BlockPos((int) Math.floor(worldPos.x), (int) Math.floor(worldPos.y), (int) Math.floor(worldPos.z));
        this.temporaryWaypoint = new Waypoint(UUID.randomUUID(), Component.translatable("xklibmc.waypoint.default_name").getString(), pos, 0xFF66CCFF, false);
        this.openDetail(service, this.temporaryWaypoint, true, clicked.event().x(), clicked.event().y());
        service.refreshInnerOverlay();
        clicked.consume();
    }

    private WaypointOverlayWidget createOverlay(WorldMapExtensionService service) {
        return new WaypointOverlayWidget(service, this.visible, () -> this.storage(service), () -> this.temporaryWaypoint, (mouse, waypoint, temporary) -> this.openDetail(service, waypoint, temporary, mouse.x, mouse.y));
    }

    private WaypointStorage storage(WorldMapExtensionService service) {
        var storage = service.currentStorage();
        if (storage == null) {
            return this.emptyStorage;
        }
        var extensionStorage = storage.getExtensionStorage(WaypointStorage.EXTENSION_ID);
        if (extensionStorage instanceof WaypointStorage waypointStorage) {
            return waypointStorage;
        }
        var waypointStorage = new WaypointStorage();
        storage.registerExtensionStorage(waypointStorage);
        return waypointStorage;
    }

    private void openDetail(WorldMapExtensionService service, Waypoint waypoint, boolean temporary) {
        this.openDetail(service, waypoint, temporary, 360, 260);
    }

    private void openDetail(WorldMapExtensionService service, Waypoint waypoint, boolean temporary, double x, double y) {
        if (this.detailWindow != null && waypoint.id().equals(this.detailWaypointId)) {
            return;
        }
        this.closeDetailWindow();
        var waypointId = waypoint.id();
        this.detailWaypointId = waypointId;
        var content = new WaypointDetailWindow(service, this.storage(service), waypoint, temporary, service::refreshInnerOverlay, () -> this.temporaryWaypoint = null, this::closeDetailWindow) {
            @Override
            public void onRemove() {
                super.onRemove();
                WaypointExtension.this.clearDetailWindow(waypointId);
            }
        };
        var title = temporary ? IComponent.translatable("xklibmc.waypoint.title.temporary") : IComponent.translatable("xklibmc.waypoint.title.detail");
        this.detailWindow = service.addSubWindow(content, title, false, (float) x, (float) y, CssLengthUnit.rpx(80), CssLengthUnit.rpx(565));
    }

    private void openManager(WorldMapExtensionService service) {
        if (this.managerWindow != null && this.managerWindow.visible()) {
            return;
        }
        this.managerWindow = service.addSubWindow(new WaypointManagerWindow(this.storage(service), waypoint -> this.openDetail(service, waypoint, false), service::refreshInnerOverlay) {
            @Override
            public void onRemove() {
                super.onRemove();
                WaypointExtension.this.managerWindow = null;
            }
        }, IComponent.translatable("xklibmc.waypoint.title.manager"), false, CssLengthUnit.rpx(180), CssLengthUnit.rpx(800));
    }

    private boolean isDetailWindowOpen() {
        return this.detailWindow != null;
    }

    private void clearDetailWindow(UUID waypointId) {
        if (waypointId.equals(this.detailWaypointId)) {
            this.detailWindow = null;
            this.detailWaypointId = null;
        }
    }
    
    private void closeDetailWindow() {
        if (this.detailWindow != null) {
            this.detailWindow.close();
        }
        this.detailWindow = null;
        this.detailWaypointId = null;
    }
}
