package com.xkball.x3dmap.client.map.waypoint;

import com.xkball.x3dmap.api.client.gui.IMapScreenContext;
import com.xkball.x3dmap.api.client.gui.IMapScreenExtension;
import com.xkball.x3dmap.api.client.gui.IMapWindow;
import com.xkball.x3dmap.api.client.gui.MapToolbarSlot;
import com.xkball.x3dmap.api.client.gui.MapWindowSpec;
import com.xkball.x3dmap.api.client.gui.input.MapInputEvent;
import com.xkball.x3dmap.client.map.storage.BuiltinMapDataTypes;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklib.ui.css.property.value.CssLengthUnit;
import com.xkball.xklib.ui.layout.BooleanLayoutVariable;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.widget.IconButton;
import com.xkball.xklib.ui.widget.IconCheckBox;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@NonNullByDefault
public final class WaypointExtension implements IMapScreenExtension {

    private final IMapScreenContext context;
    private final BooleanLayoutVariable visible = new BooleanLayoutVariable(true);
    private final WaypointStorage emptyStorage = new WaypointStorage();
    private @Nullable Waypoint temporaryWaypoint;
    private @Nullable IMapWindow managerWindow;
    private @Nullable IMapWindow detailWindow;
    private @Nullable UUID detailWaypointId;
    private boolean addingWaypoint;

    public WaypointExtension(IMapScreenContext context) {
        this.context = context;
    }

    @Override
    public void onOpen() {
        this.visible.addCallback(_ -> this.context.gui().refreshOverlays());
        this.context.gui().addToolbarWidget(MapToolbarSlot.LEFT,
                new IconCheckBox(VanillaUtils.modrl("icon/waypoint")).bind(this.visible)
                        .withTooltip(IComponent.translatable("xklibmc.waypoint.show_waypoints")));
        this.context.gui().addToolbarWidget(MapToolbarSlot.TOP_SECONDARY,
                new IconButton(VanillaUtils.modrl("icon/add_waypoint"), () -> this.addingWaypoint = true)
                        .withTooltip(IComponent.translatable("xklibmc.waypoint.add_waypoint")));
        this.context.gui().addToolbarWidget(MapToolbarSlot.TOP_SECONDARY,
                new IconButton(VanillaUtils.modrl("icon/manage_waypoint"), this::openManager)
                        .withTooltip(IComponent.translatable("xklibmc.waypoint.open_manager")));
        this.context.gui().addToolbarWidget(MapToolbarSlot.TOP_SECONDARY, new Widget().setCSSClassName("splitter"));
        this.context.gui().setOverlay(this.context.extensionId(), () -> this.createOverlay());
        this.context.gui().refreshOverlays();
    }

    @Override
    public void close() {
        this.managerWindow = null;
        this.detailWindow = null;
        this.detailWaypointId = null;
        this.temporaryWaypoint = null;
        this.addingWaypoint = false;
    }

    @Override
    public boolean handle(MapInputEvent event) {
        if (!(event instanceof MapInputEvent.MouseClicked clicked) || clicked.event().button() != 0) {
            return false;
        }
        if (this.addingWaypoint) {
            var worldPos = this.context.view().screenToWorld(clicked.event().x(), clicked.event().y());
            if (worldPos == null) {
                return false;
            }
            var pos = new BlockPos((int) Math.floor(worldPos.x), (int) Math.floor(worldPos.y), (int) Math.floor(worldPos.z));
            this.temporaryWaypoint = new Waypoint(UUID.randomUUID(), Component.translatable("xklibmc.waypoint.default_name").getString(), pos, 0xFF66CCFF, false);
            this.openDetail(this.temporaryWaypoint, true, clicked.event().x(), clicked.event().y());
            this.context.gui().refreshOverlays();
            this.addingWaypoint = false;
            return true;
        }
        if (!clicked.doubleClick()) {
            return false;
        }
        var worldPos = this.context.view().screenToWorld(clicked.event().x(), clicked.event().y());
        if (worldPos == null || this.detailWindow != null) {
            return this.detailWindow != null;
        }
        var pos = new BlockPos((int) Math.floor(worldPos.x), (int) Math.floor(worldPos.y), (int) Math.floor(worldPos.z));
        this.temporaryWaypoint = new Waypoint(UUID.randomUUID(), Component.translatable("xklibmc.waypoint.default_name").getString(), pos, 0xFF66CCFF, false);
        this.openDetail(this.temporaryWaypoint, true, clicked.event().x(), clicked.event().y());
        this.context.gui().refreshOverlays();
        return true;
    }

    private WaypointOverlayWidget createOverlay() {
        return new WaypointOverlayWidget(this.context.view(), this.visible, this::storage, () -> this.temporaryWaypoint,
                (mouse, waypoint, temporary) -> this.openDetail(waypoint, temporary, mouse.x, mouse.y));
    }

    private WaypointStorage storage() {
        var access = this.context.runtime().storage().currentLevelData();
        if (access.isEmpty()) {
            return this.emptyStorage;
        }
        return access.get().get(BuiltinMapDataTypes.WAYPOINTS).value();
    }

    private void openDetail(Waypoint waypoint, boolean temporary) {
        this.openDetail(waypoint, temporary, 360, 260);
    }

    private void openDetail(Waypoint waypoint, boolean temporary, double x, double y) {
        if (this.detailWindow != null && waypoint.id().equals(this.detailWaypointId)) {
            return;
        }
        this.closeDetailWindow();
        var waypointId = waypoint.id();
        this.detailWaypointId = waypointId;
        var content = new WaypointDetailWindow(this.context.gui(), this.storage(), waypoint, temporary,
                this.context.gui()::refreshOverlays,
                () -> this.temporaryWaypoint = null,
                this::closeDetailWindow) {
            @Override
            public void onRemove() {
                super.onRemove();
                WaypointExtension.this.clearDetailWindow(waypointId);
            }
        };
        var title = temporary ? IComponent.translatable("xklibmc.waypoint.title.temporary") : IComponent.translatable("xklibmc.waypoint.title.detail");
        this.detailWindow = this.context.gui().openWindow(MapWindowSpec.regular(title, false, (float) x, (float) y, CssLengthUnit.rpx(80), CssLengthUnit.rpx(565)), content);
    }

    private void openManager() {
        if (this.managerWindow != null && this.managerWindow.visible()) {
            return;
        }
        this.managerWindow = this.context.gui().openWindow(
                MapWindowSpec.regular(IComponent.translatable("xklibmc.waypoint.title.manager"), false, CssLengthUnit.rpx(180), CssLengthUnit.rpx(800)),
                new WaypointManagerWindow(this.storage(), waypoint -> this.openDetail(waypoint, false), this.context.gui()::refreshOverlays) {
                    @Override
                    public void onRemove() {
                        super.onRemove();
                        WaypointExtension.this.managerWindow = null;
                    }
                }
        );
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
