package com.xkball.x3dmap.ui;

import com.xkball.x3dmap.block.entity.TerrainProjectorBlockEntity;
import com.xkball.x3dmap.network.c2s.UpdateBlockEntityData;
import com.xkball.x3dmap.ui.widget.TerrainProjectorPipWidget;
import com.xkball.xklib.api.gui.input.IMouseButtonEvent;
import com.xkball.xklib.ui.layout.IntLayoutVariable;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.render.IGUIGraphics;
import com.xkball.xklib.ui.widget.Label;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklib.ui.widget.container.ContainerWidget;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.ui.XKLibBaseScreen;
import com.xkball.xklibmc.ui.widget.NumberInputWidget;
import com.xkball.xklibmc.ui.widget.WidgetWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

@NonNullByDefault
public final class TerrainProjectorScreen extends XKLibBaseScreen {

    private final TerrainProjectorBlockEntity blockEntity;
    private final NumberInputWidget<Integer> centerX;
    private final NumberInputWidget<Integer> centerZ;
    private final NumberInputWidget<Integer> radius;
    private final NumberInputWidget<Float> yOffset;
    private final IntLayoutVariable lodLevel = new IntLayoutVariable();

    public TerrainProjectorScreen(TerrainProjectorBlockEntity blockEntity) {
        super(Component.translatable("xklibmc.terrain_projector.title"));
        this.blockEntity = blockEntity;
        this.centerX = NumberInputWidget.ofInt(-30000000, 30000000, 1);
        this.centerZ = NumberInputWidget.ofInt(-30000000, 30000000, 1);
        this.radius = NumberInputWidget.ofInt(0, 32, 1);
        this.yOffset = NumberInputWidget.ofFloat(-2048.0F, 2048.0F, 0.1F);
        this.centerX.setValue(blockEntity.centerPos.getX());
        this.centerZ.setValue(blockEntity.centerPos.getZ());
        this.radius.setValue(blockEntity.projectionRadius);
        this.yOffset.setValue(blockEntity.yOffset);
        this.lodLevel.set(Math.clamp(blockEntity.lodLevel, 0, 4));
        this.centerX.setCallback(_ -> this.updatePreview());
        this.centerZ.setCallback(_ -> this.updatePreview());
        this.radius.setCallback(_ -> this.updatePreview());
        this.yOffset.setCallback(_ -> this.updatePreview());
        this.lodLevel.addCallback(_ -> this.updatePreview());
        this.addScreenLayer(this.createLayout());
    }

    private void updatePreview() {
        this.blockEntity.setParameters(
                new BlockPos(this.centerX.getValue(), 0, this.centerZ.getValue()),
                this.radius.getValue(),
                this.lodLevel.get(),
                this.yOffset.getValue()
        );
    }

    private Widget createLayout() {
        var left = new ContainerWidget()
                .inlineStyle("""
                        flex-direction: column;
                        size: 100% 100%;
                        padding: 5rpx;
                        overflow-y: scroll;
                        scrollbar-width: 6;
                        """)
                .asRootStyle("""
                        Label {
                            text-color: -1;
                            text-height: 9rpx;
                            flex-shrink: 0;
                        }
                        NumberInputWidget {
                            size: 65% 14rpx;
                            flex-shrink: 0;
                        }
                        .input_line {
                            flex-direction: row;
                            size: 100% 16rpx;
                            align-items: center;
                            flex-shrink: 0;
                            margin-bottom: 3rpx;
                        }
                        .input_label {
                            size: 35% 100%;
                            text-align: left;
                        }
                        .lod_section {
                            flex-direction: column;
                            size: 100% auto;
                            flex-shrink: 0;
                            margin-top: 3rpx;
                        }
                        .lod_title {
                            size: 100% 16rpx;
                        }
                        .lod_options {
                            flex-direction: column;
                            size: 100% auto;
                            flex-shrink: 0;
                        }
                        .lod_option {
                            flex-direction: row;
                            size: 100% 18rpx;
                            align-items: center;
                            flex-shrink: 0;
                        }
                        .lod_value {
                            size: 100% 16rpx;
                            margin-left: 4rpx;
                            text-align: left;
                            flex-shrink: 0;
                        }
                        """)
                .addChild(this.inputLine(new Label(IComponent.translatable("xklibmc.terrain_projector.center_x")), this.centerX))
                .addChild(this.inputLine(new Label(IComponent.translatable("xklibmc.terrain_projector.center_z")), this.centerZ))
                .addChild(this.inputLine(new Label(IComponent.translatable("xklibmc.terrain_projector.radius")
                        //label的tooltip创建暂时有bug
                        .withTooltip(() -> Widget.createTooltipFactory(IComponent.translatable("xklibmc.terrain_projector.radius_unit")).get())), this.radius))
                .addChild(this.inputLine(new Label(IComponent.translatable("xklibmc.terrain_projector.y_offset")), this.yOffset))
                .addChild(this.createLodLine())
                .addChild(WidgetWrapper.button(Component.translatable("xklibmc.common.done"), _ -> this.closeScreen())
                        .inlineStyle("size: 70% 20rpx; align-self: center; margin-top: 8rpx;"));
        var right = new TerrainProjectorPipWidget(this.blockEntity).inlineStyle("size: 100% 100%;");
        return XKLibBaseScreen.biPanelFrame(IComponent.translatable("xklibmc.terrain_projector.title"), left, right);
    }

    private ContainerWidget inputLine(Widget label, NumberInputWidget<?> input) {
        return new ContainerWidget()
                .setCSSClassName("input_line")
                .addChild(label.setCSSClassName("input_label"))
                .addChild(input);
    }

    private ContainerWidget createLodLine() {
        var line = new ContainerWidget().setCSSClassName("lod_section")
                .addChild(new Label(IComponent.translatable("xklibmc.terrain_projector.lod")).setCSSClassName("lod_title"));
        var options = new ContainerWidget().setCSSClassName("lod_options");
        for (int i = 0; i <= 4; i++) {
            options.addChild(new LodOptionWidget(i, this.lodLevel)
                    .setCSSClassName("lod_option")
                    .addChild(new Label(Integer.toString(i)).setCSSClassName("lod_value")));
        }
        line.addChild(options);
        return line;
    }

    @NonNullByDefault
    private static final class LodOptionWidget extends ContainerWidget {

        private final int value;
        private final IntLayoutVariable selectedValue;

        private LodOptionWidget(int value, IntLayoutVariable selectedValue) {
            this.value = value;
            this.selectedValue = selectedValue;
        }

        @Override
        public void doRender(IGUIGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (this.selectedValue.getAsInt() == this.value) {
                graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, 0xCC1D2B20);
                graphics.renderOutline(this.x, this.y, this.width, this.height, 0xFF76A36E);
            } else if (this.isMouseOver(mouseX, mouseY)) {
                graphics.renderOutline(this.x, this.y, this.width, this.height, 0xFFFFFFFF);
            }
            super.doRender(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        protected boolean onMouseClicked(IMouseButtonEvent event, boolean doubleClick) {
            if (event.button() != 0) {
                return false;
            }
            this.selectedValue.setAsInt(this.value);
            return true;
        }
    }

    private void closeScreen() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void removed() {
        this.updatePreview();
        ClientPacketDistributor.sendToServer(UpdateBlockEntityData.create(this.blockEntity));
        super.removed();
    }
}
