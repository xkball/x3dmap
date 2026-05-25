package com.xkball.x3dmap.client.map.waypoint;

import com.xkball.xklib.ui.css.property.value.CssLengthUnit;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.render.IGUIGraphics;
import com.xkball.xklib.ui.widget.Button;
import com.xkball.xklib.ui.widget.Label;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklib.ui.widget.container.ContainerWidget;
import com.xkball.xklib.ui.widget.container.WindowedContainer;
import com.xkball.xklibmc.ui.widget.ColorInputWidget;
import com.xkball.xklibmc.ui.widget.NumberInputWidget;
import com.xkball.xklibmc.ui.widget.ObjectInputWidget;
import com.xkball.x3dmap.api.client.map.WorldMapExtensionService;
import net.minecraft.core.BlockPos;

import java.util.UUID;

public class WaypointDetailWindow extends ContainerWidget {

    private final boolean temporary;
    private final Runnable changed;
    private final Runnable removeTemporary;
    private boolean temporaryResolved;

    public WaypointDetailWindow(WorldMapExtensionService service, WaypointStorage storage, Waypoint waypoint, boolean temporary, Runnable changed, Runnable removeTemporary, Runnable closeWindow) {
        this.temporary = temporary;
        this.changed = changed;
        this.removeTemporary = removeTemporary;
        this.inlineStyle("""
                flex-direction: column;
                size: 100% 100%;
                align-items: center;
                """)
                .asRootStyle("""
                        Label {
                            text-color: -1;
                            flex-shrink: 0;
                        }
                        .action_btn {
                            size: 100% 12rpx;
                            margin-bottom: 2rpx;
                            text-align: center;
                            text-scale: expand-width;
                            button-shape: rect;
                            button-bg-color: rgb(229,233,239);
                            text-drop-shadow: false;
                            text-extra-width: 2rpx;
                            text-height: 8rpx;
                        }
                        ObjectInputWidget {
                            size: 100% 12rpx;
                            margin-bottom: 2rpx;
                            flex-shrink: 0;
                        }
                        NumberInputWidget {
                            size: 100% 12rpx;
                            margin-bottom: 2rpx;
                            flex-shrink: 0;
                        }
                        .color_row {
                            flex-direction: row;
                            size: 100% 14rpx;
                            margin-bottom: 2rpx;
                            flex-shrink: 0;
                        }
                        .color_preview {
                            size: 50% 100%;
                            border: 1rpx;
                            border-color: 0xEEAAAAAA;
                        }
                        .color_edit_btn {
                            size: 50% 100%;
                            text-align: center;
                            text-scale: expand-width;
                            button-shape: rect;
                            button-bg-color: rgb(229,233,239);
                            text-drop-shadow: false;
                            text-extra-width: 2rpx;
                            text-height: 8rpx;
                        }
                        """);
        var name = ObjectInputWidget.ofString();
        name.setAsString(waypoint.name());
        var x = NumberInputWidget.ofInt(-30000000, 30000000, 1);
        var y = NumberInputWidget.ofInt(-2048, 2048, 1);
        var z = NumberInputWidget.ofInt(-30000000, 30000000, 1);
        x.setValue(waypoint.pos().getX());
        y.setValue(waypoint.pos().getY());
        z.setValue(waypoint.pos().getZ());
        name.setCallback(_ -> {
            waypoint.setName(name.getAsString());
            this.markDirtyIfFormal(storage, temporary);
            changed.run();
        });
        var posCallback = (Runnable) () -> {
            waypoint.setPos(new BlockPos(x.getValue(), y.getValue(), z.getValue()));
            this.markDirtyIfFormal(storage, temporary);
            changed.run();
        };
        x.setCallback(_ -> posCallback.run());
        y.setCallback(_ -> posCallback.run());
        z.setCallback(_ -> posCallback.run());
        var colorRow = this.createColorRow(service, storage, waypoint);
        var editor = new ContainerWidget()
                .inlineStyle("flex-direction: column; size: 90% auto; margin: 3rpx;")
                .addChild(new Label(IComponent.translatable("xklibmc.waypoint.name")).inlineStyle("height: 8rpx;"))
                .addChild(name)
                .addChild(new Label(IComponent.translatable("xklibmc.waypoint.detail.x")).inlineStyle("height: 8rpx;"))
                .addChild(x)
                .addChild(new Label(IComponent.translatable("xklibmc.waypoint.detail.y")).inlineStyle("height: 8rpx;"))
                .addChild(y)
                .addChild(new Label(IComponent.translatable("xklibmc.waypoint.detail.z")).inlineStyle("height: 8rpx;"))
                .addChild(z)
                .addChild(new Label(IComponent.translatable("xklibmc.waypoint.detail.color")).inlineStyle("height: 8rpx;"))
                .addChild(colorRow);
        var actions = new ContainerWidget().inlineStyle("flex-direction: column; size: 90% auto; margin: 3rpx;");
        var teleport = new Button(IComponent.translatable("xklibmc.waypoint.teleport"), () -> WaypointActions.teleport(waypoint)).setCSSClassName("action_btn");
        teleport.setEnabled(WaypointActions.canTeleport());
        actions.addChild(teleport);
        actions.addChild(new Button(IComponent.translatable("xklibmc.waypoint.share"), () -> WaypointActions.share(waypoint)).setCSSClassName("action_btn"));
        if (!temporary) {
            actions.addChild(new Button(IComponent.translatable(waypoint.hidden() ? "xklibmc.waypoint.show" : "xklibmc.waypoint.hide"), () -> {
                waypoint.setHidden(!waypoint.hidden());
                storage.markDirty();
                changed.run();
            }).setCSSClassName("action_btn"));
        }
        actions.addChild(new Button(IComponent.translatable("xklibmc.waypoint.delete"), () -> {
            if (temporary) {
                this.temporaryResolved = true;
                removeTemporary.run();
            } else {
                storage.remove(waypoint);
            }
            changed.run();
            closeWindow.run();
        }).setCSSClassName("action_btn"));
        if (temporary) {
            actions.addChild(new Button(IComponent.translatable("xklibmc.waypoint.save"), () -> {
                var formal = new Waypoint(UUID.randomUUID(), waypoint.name(), waypoint.pos(), waypoint.color(), false);
                storage.add(formal);
                this.temporaryResolved = true;
                removeTemporary.run();
                changed.run();
                closeWindow.run();
            }).setCSSClassName("action_btn"));
        }
        this.addChild(editor);
        this.addChild(actions);
    }

    @Override
    public void onRemove() {
        if (this.temporary && !this.temporaryResolved) {
            this.temporaryResolved = true;
            this.removeTemporary.run();
            this.changed.run();
        }
        super.onRemove();
    }

    private void markDirtyIfFormal(WaypointStorage storage, boolean temporary) {
        if (!temporary) {
            storage.markDirty();
        }
    }

    private Widget createColorRow(WorldMapExtensionService service, WaypointStorage storage, Waypoint waypoint) {
        return new ContainerWidget()
                .setCSSClassName("color_row")
                .addChild(new ColorPreviewWidget(waypoint).setCSSClassName("color_preview"))
                .addChild(new Button(IComponent.translatable("xklibmc.waypoint.edit"), () -> this.openColorWindow(service, storage, waypoint)).setCSSClassName("color_edit_btn"));
    }

    private void openColorWindow(WorldMapExtensionService service, WaypointStorage storage, Waypoint waypoint) {
        var colorInput = new ColorInputWidget();
        colorInput.setValue(waypoint.color());
        var holder = new WindowedContainer.SubWindow[1];
        var content = new ContainerWidget()
                .inlineStyle("""
                        flex-direction: column;
                        size: 100% 100%;
                        """)
                .asRootStyle("""
                        ColorInputWidget {
                            size: 100% 80rpx;
                        }
                        .color_actions {
                            size: 100% 16rpx;
                            flex-direction: row;
                        }
                        .color_action_btn {
                            margin: 1rpx;
                            size: 50% 100%;
                            text-align: center;
                            text-scale: expand-width;
                            button-shape: rect;
                            button-bg-color: rgb(229,233,239);
                            text-drop-shadow: false;
                            text-extra-width: 2rpx;
                            text-height: 8rpx;
                        }
                        """)
                .addChild(colorInput)
                .addChild(new ContainerWidget()
                        .setCSSClassName("color_actions")
                        .addChild(new Button(IComponent.translatable("xklibmc.common.cancel"), () -> holder[0].close()).setCSSClassName("color_action_btn"))
                        .addChild(new Button(IComponent.translatable("xklibmc.waypoint.confirm"), () -> {
                            waypoint.setColor(colorInput.getValue());
                            this.markDirtyIfFormal(storage, this.temporary);
                            this.changed.run();
                            holder[0].close();
                        }).setCSSClassName("color_action_btn")));
        holder[0] = service.addBlockingSubWindow(content, IComponent.translatable("xklibmc.waypoint.detail.color_title"), false, CssLengthUnit.rpx(140), CssLengthUnit.rpx(240));
    }

    private static class ColorPreviewWidget extends Widget {

        private final Waypoint waypoint;

        private ColorPreviewWidget(Waypoint waypoint) {
            this.waypoint = waypoint;
        }

        @Override
        public void doRender(IGUIGraphics graphics, int mouseX, int mouseY, float a) {
            super.doRender(graphics, mouseX, mouseY, a);
            graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, this.waypoint.color());
        }
    }
}
