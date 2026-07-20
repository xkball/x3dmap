package com.xkball.x3dmap.client.map.waypoint;

import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.widget.Button;
import com.xkball.xklib.ui.widget.Label;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklib.ui.widget.container.ContainerWidget;
import com.xkball.xklibmc.annotation.NonNullByDefault;

import java.util.function.Consumer;

@NonNullByDefault
public class WaypointManagerWindow extends ContainerWidget {

    private final WaypointStorage storage;
    private final Consumer<Waypoint> openDetail;
    private final Runnable changed;
    private final Runnable dirtyListener;

    public WaypointManagerWindow(WaypointStorage storage, Consumer<Waypoint> openDetail, Runnable changed) {
        this.storage = storage;
        this.openDetail = openDetail;
        this.changed = changed;
        this.dirtyListener = () -> this.submitTreeUpdate(this::rebuild);
        this.storage.addDirtyListener(this.dirtyListener);
        this.inlineStyle("""
                        flex-direction: column;
                        size: 100% 100%;
                        max-size: 180rpx 200rpx;
                        min-size: 180rpx 100rpx;
                        """)
                .asRootStyle("""
                        Label {
                            flex-shrink: 0;
                            text-scale: expand-width;
                            text-drop-shadow: false;
                            text-align: center;
                        }
                        Button {
                            width: 30%;
                            height: 12rpx;
                            text-align: center;
                            text-scale: expand-width;
                            button-shape: rect;
                            button-bg-color: rgb(229,233,239);
                            text-drop-shadow: false;
                            text-extra-width: 2rpx;
                        }
                        .row {
                            flex-direction: row;
                            width: 100%;
                            height: 16rpx;
                            flex-shrink: 0;
                            border-bottom: 1rpx;
                            border-color: 0xEEAAAAAA;
                        }
                        .row_data {
                            flex-direction: row;
                            width: 65%;
                            height: 100%;
                            flex-shrink: 1;
                        }
                        .name_cell {
                            width: 40%;
                            height: 100%;
                            border-left: 1rpx;
                            border-right: 1rpx;
                            border-color: 0xEEAAAAAA;
                        }
                        .pos_cell {
                            width: 60%;
                            height: 100%;
                            border-right: 1rpx;
                            border-color: 0xEEAAAAAA;
                        }
                        .actions_cell {
                            flex-direction: row;
                            width: 35%;
                            height: 100%;
                            flex-shrink: 0;
                            align-items: center;
                            justify-content: space-around;
                            border-right: 1rpx;
                            border-color: 0xEEAAAAAA;
                        }
                        """);
        this.rebuild();
    }

    private void rebuild() {
        this.clearChildren();
        this.addChild(this.header());
        var rows = new ContainerWidget()
                .inlineStyle("""
                        flex-direction: column;
                        width: 100%;
                        height: 100%-16rpx;
                        overflow-y: scroll;
                        scrollbar-width: 8;
                        """);
        for (var waypoint : this.storage.waypoints()) {
            rows.addChild(this.row(waypoint));
        }
        rows.addChild(this.emptyRow());
        this.addChild(rows);
    }

    private Widget header() {
        return new ContainerWidget()
                .inlineStyle("""
                        flex-direction: row;
                        width: 100%-8px;
                        height: 16rpx;
                        flex-shrink: 0;
                        border-top: 1rpx;
                        border-bottom: 1rpx;
                        border-color: 0xEEAAAAAA;
                        background-color: 0x55334155;
                        """)
                .addChild(new ContainerWidget()
                        .setCSSClassName("row_data")
                        .addChild(new Label(IComponent.translatable("xklibmc.waypoint.name")).setCSSClassName("name_cell").inlineStyle("text-color: -1;"))
                        .addChild(new Label(IComponent.translatable("xklibmc.waypoint.manager.pos")).setCSSClassName("pos_cell").inlineStyle("text-color: -1;")))
                .addChild(new ContainerWidget()
                        .setCSSClassName("actions_cell")
                        .addChild(new Label(IComponent.translatable("xklibmc.waypoint.manager.actions")).inlineStyle("width: 100%; height: 100%; text-color: -1;")));
    }

    private Widget row(Waypoint waypoint) {
        var teleport = new Button(IComponent.translatable("xklibmc.waypoint.manager.tp"), () -> WaypointActions.teleport(waypoint));
        teleport.setEnabled(WaypointActions.canTeleport());
        var name = new Label(waypoint.name(), this.textColor(waypoint));
        name.setCSSClassName("name_cell");
        return new ContainerWidget()
                .setCSSClassName("row")
                .addChild(new ContainerWidget()
                        .setCSSClassName("row_data")
                        .addChild(name)
                        .addChild(new Label(waypoint.pos().toShortString()).setCSSClassName("pos_cell").inlineStyle("text-color: -1;")))
                .addChild(new ContainerWidget()
                        .setCSSClassName("actions_cell")
                        .addChild(teleport)
                        .addChild(new Button(IComponent.translatable(waypoint.hidden() ? "xklibmc.waypoint.show" : "xklibmc.waypoint.hide"), () -> {
                            waypoint.setHidden(!waypoint.hidden());
                            this.storage.markDirty();
                            this.changed.run();
                        }))
                        .addChild(new Button(IComponent.translatable("xklibmc.waypoint.edit"), () -> this.openDetail.accept(waypoint)))
                );
    }

    private Widget emptyRow() {
        return new ContainerWidget().inlineStyle("size: 100% 16rpx;");
    }

    private int textColor(Waypoint waypoint) {
        return 0xFF000000 | (waypoint.color() & 0xFFFFFF);
    }

    @Override
    public void onRemove() {
        super.onRemove();
        this.storage.removeDirtyListener(this.dirtyListener);
    }
}
