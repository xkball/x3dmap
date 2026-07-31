package com.xkball.x3dmap.compat.smu;

import com.xkball.x3dmap.api.client.gui.IMapWindow;
import com.xkball.x3dmap.api.client.gui.IMapScreenContext;
import com.xkball.x3dmap.api.client.gui.IMapScreenExtension;
import com.xkball.x3dmap.api.client.gui.MapToolbarSlot;
import com.xkball.x3dmap.api.client.gui.MapWindowRefContainer;
import com.xkball.x3dmap.api.client.gui.MapWindowSpec;
import com.xkball.x3dmap.api.client.map.WaypointOverlayCreateEvent;
import com.xkball.xklib.ui.css.property.value.CssLengthUnit;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.widget.Button;
import com.xkball.xklib.ui.widget.IconCheckBox;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.utils.VanillaUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector2d;
import org.jspecify.annotations.Nullable;
import org.teacon.exhibition_portal.components.ExhibitionMetadata;
import org.teacon.exhibition_portal.network.TeleportToExhibitionPacket;

import java.util.UUID;

@NonNullByDefault
public final class SmuScreenExtension implements IMapScreenExtension {

    private final IMapScreenContext context;
    private boolean visible;
    private boolean registered;
    private @Nullable IMapWindow exhibitionWindow;
    private @Nullable UUID exhibitionId;

    public SmuScreenExtension(IMapScreenContext context) {
        this.context = context;
    }

    @Override
    public void onOpen() {
        this.visible = this.context.getBooleanState("visible", true);
        var toggle = new IconCheckBox(VanillaUtils.convertId(SmuCompatPlugin.LABEL_TOGGLE_SPRITE));
        toggle.setValue(this.visible);
        toggle.onChange = () -> {
            this.visible = toggle.getValue();
            this.context.setBooleanState("visible", this.visible);
            this.context.gui().refreshOverlays();
        };
        toggle.withTooltip(IComponent.translatable("x3d_map_compat_smu.layer.exhibitions"));
        this.context.gui().addToolbarWidget(MapToolbarSlot.LEFT, toggle);
        NeoForge.EVENT_BUS.register(this);
        this.registered = true;
        this.context.gui().refreshOverlays();
    }

    @Override
    public void close() {
        if (this.registered) {
            NeoForge.EVENT_BUS.unregister(this);
            this.registered = false;
        }
        this.exhibitionWindow = null;
        this.exhibitionId = null;
    }

    @SubscribeEvent
    public void addExhibitions(WaypointOverlayCreateEvent event) {
        if (!this.visible) {
            return;
        }
        for (var metadata : SmuClientData.labels()) {
            event.add(new SmuExhibitionWidget(metadata, mouse -> this.openExhibition(metadata, mouse)));
        }
    }

    private void openExhibition(ExhibitionMetadata metadata, Vector2d mouse) {
        if (this.exhibitionWindow != null && metadata.uuid().equals(this.exhibitionId)) {
            return;
        }
        if (this.exhibitionWindow != null) {
            this.exhibitionWindow.close();
        }
        var exhibitionId = metadata.uuid();
        this.exhibitionId = exhibitionId;
        var content = new MapWindowRefContainer() {
            @Override
            public void onRemove() {
                super.onRemove();
                SmuScreenExtension.this.clearExhibitionWindow(exhibitionId);
            }
        };
        content.inlineStyle("size: 100% 100%; align-items: center; justify-content: center;")
                .addChild(new Button(IComponent.translatable("exhibition_portal.detail.teleport"),
                        () -> ClientPacketDistributor.sendToServer(new TeleportToExhibitionPacket(exhibitionId)))
                        .inlineStyle("size: 90% 12rpx; text-align: center; text-scale: expand-width; button-shape: rect; button-bg-color: rgb(229,233,239); text-drop-shadow: false; text-extra-width: 2rpx; text-height: 8rpx;"));
        this.exhibitionWindow = this.context.gui().openWindow(
                MapWindowSpec.regular(IComponent.literal(metadata.name()), false, (float) mouse.x, (float) mouse.y,
                        CssLengthUnit.rpx(100), CssLengthUnit.rpx(30)),
                content
        );
    }

    private void clearExhibitionWindow(UUID exhibitionId) {
        if (exhibitionId.equals(this.exhibitionId)) {
            this.exhibitionWindow = null;
            this.exhibitionId = null;
        }
    }
}
