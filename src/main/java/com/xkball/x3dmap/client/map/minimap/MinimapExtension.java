package com.xkball.x3dmap.client.map.minimap;

import com.xkball.x3dmap.ClientConfig;
import com.xkball.x3dmap.api.client.gui.IMapScreenContext;
import com.xkball.x3dmap.api.client.gui.IMapScreenExtension;
import com.xkball.x3dmap.api.client.gui.IMapWindow;
import com.xkball.x3dmap.api.client.gui.MapToolbarSlot;
import com.xkball.x3dmap.api.client.gui.MapWindowSpec;
import com.xkball.x3dmap.ui.widget.IntSliderWidget;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklib.ui.css.property.value.CssLengthUnit;
import com.xkball.xklib.ui.layout.BooleanLayoutVariable;
import com.xkball.xklib.ui.layout.IntLayoutVariable;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.widget.CheckBox;
import com.xkball.xklib.ui.widget.IconButton;
import com.xkball.xklib.ui.widget.Label;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklib.ui.widget.container.ContainerWidget;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.jspecify.annotations.Nullable;

@NonNullByDefault
public final class MinimapExtension implements IMapScreenExtension {

    private final IMapScreenContext context;
    private final IntLayoutVariable renderInterval = new IntLayoutVariable(10);
    private final IntLayoutVariable minimapSize = new IntLayoutVariable(25);
    private final IntLayoutVariable minimapPaddingX = new IntLayoutVariable(5);
    private final IntLayoutVariable minimapPaddingY = new IntLayoutVariable(5);
    private final BooleanLayoutVariable rotateWithPlayer = new BooleanLayoutVariable(false);
    private final BooleanLayoutVariable minimapEnabled = new BooleanLayoutVariable(true);
    private @Nullable IMapWindow configWindow;

    public MinimapExtension(IMapScreenContext context) {
        this.context = context;
    }

    @Override
    public void onOpen() {
        this.renderInterval.set(ClientConfig.MINIMAP_RENDER_INTERVAL.get());
        this.minimapSize.set(ClientConfig.MINIMAP_SIZE.get());
        this.minimapPaddingX.set(ClientConfig.MINIMAP_PADDING_X.get());
        this.minimapPaddingY.set(ClientConfig.MINIMAP_PADDING_Y.get());
        this.rotateWithPlayer.set(ClientConfig.MINIMAP_ROTATE_WITH_PLAYER.get());
        this.minimapEnabled.set(ClientConfig.MINIMAP_ENABLED.get());
        this.renderInterval.addCallback(ClientConfig.MINIMAP_RENDER_INTERVAL::set);
        this.minimapSize.addCallback(ClientConfig.MINIMAP_SIZE::set);
        this.minimapPaddingX.addCallback(ClientConfig.MINIMAP_PADDING_X::set);
        this.minimapPaddingY.addCallback(ClientConfig.MINIMAP_PADDING_Y::set);
        this.rotateWithPlayer.addCallback(ClientConfig.MINIMAP_ROTATE_WITH_PLAYER::set);
        this.minimapEnabled.addCallback(ClientConfig.MINIMAP_ENABLED::set);
        this.context.gui().addToolbarWidget(MapToolbarSlot.TOP_SECONDARY,
                new IconButton(VanillaUtils.modrl("icon/minimap"), this::openConfig)
                        .withTooltip(IComponent.translatable("xklibmc.minimap.open_settings")));
        this.context.gui().addToolbarWidget(MapToolbarSlot.TOP_SECONDARY, new Widget().setCSSClassName("splitter"));
    }

    @Override
    public void close() {
        this.configWindow = null;
        this.renderInterval.removeCallbacks();
        this.minimapSize.removeCallbacks();
        this.minimapPaddingX.removeCallbacks();
        this.minimapPaddingY.removeCallbacks();
        this.rotateWithPlayer.removeCallbacks();
        this.minimapEnabled.removeCallbacks();
        ClientConfig.SPEC.save();
    }

    private void openConfig() {
        if (this.configWindow != null && this.configWindow.visible()) {
            return;
        }
        this.configWindow = this.context.gui().openWindow(
                MapWindowSpec.regular(
                        IComponent.translatable("xklibmc.minimap.config.title"),
                        false,
                        CssLengthUnit.rpx(140),
                        CssLengthUnit.rpx(240)
                ),
                this.createConfigContent()
        );
    }

    private Widget createConfigContent() {
        var preview = new MinimapPreviewWidget(this.rotateWithPlayer)
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
                .addChild(new ContainerWidget()
                        .setCSSClassName("minimap_row")
                        .addChild(new Label(IComponent.translatable("xklibmc.minimap.config.rotate")).setCSSClassName("minimap_label"))
                        .addChild(new CheckBox().bind(this.rotateWithPlayer)))
                .addChild(this.sliderRow(
                        IComponent.translatable("xklibmc.minimap.config.size"),
                        IComponent.translatable("xklibmc.minimap.config.size.tooltip"),
                        this.minimapSize,
                        1,
                        50
                ))
                .addChild(this.sliderRow(
                        IComponent.translatable("xklibmc.minimap.config.padding_x"),
                        IComponent.translatable("xklibmc.minimap.config.padding_x.tooltip"),
                        this.minimapPaddingX,
                        0,
                        100
                ))
                .addChild(this.sliderRow(
                        IComponent.translatable("xklibmc.minimap.config.padding_y"),
                        IComponent.translatable("xklibmc.minimap.config.padding_y.tooltip"),
                        this.minimapPaddingY,
                        0,
                        100
                ))
                .addChild(this.sliderRow(IComponent.translatable("xklibmc.minimap.config.render_interval"), this.renderInterval, 1, 20));
    }

    private Widget sliderRow(IComponent label, IntLayoutVariable variable, int min, int max) {
        return new ContainerWidget()
                .setCSSClassName("minimap_row")
                .addChild(new Label(label).setCSSClassName("minimap_label"))
                .addChild(new IntSliderWidget(min, max, variable.get()).bind(variable).inlineStyle("size: 50% 12rpx;margin-right: 2rpx;"));
    }

    private Widget sliderRow(IComponent label, IComponent tooltip, IntLayoutVariable variable, int min, int max) {
        return new ContainerWidget()
                .setCSSClassName("minimap_row")
                .addChild(new Label(label).setCSSClassName("minimap_label").withTooltip(tooltip))
                .addChild(new IntSliderWidget(min, max, variable.get()).bind(variable).inlineStyle("size: 50% 12rpx;margin-right: 2rpx;"));
    }
}
