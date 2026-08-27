package com.xkball.x3dmap.ui.widget;

import com.xkball.x3dmap.api.client.render.MapViewportPresets;
import com.xkball.x3dmap.api.client.viewport.MapCameraState;
import com.xkball.x3dmap.block.entity.TerrainProjectorBlockEntity;
import com.xkball.x3dmap.client.map.viewport.MapCameraController;
import com.xkball.x3dmap.client.map.viewport.MapFrameSnapshot;
import com.xkball.x3dmap.client.render.pip.TerrainProjectorPipRenderer;
import com.xkball.x3dmap.client.terrain.TerrainMapManager;
import com.xkball.xklib.api.gui.input.IMouseButtonEvent;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.render.IGUIGraphics;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.ui.XKLibBaseScreen;
import com.xkball.xklibmc.x3d.backend.b3d.B3dGuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;

@NonNullByDefault
public final class TerrainProjectorPipWidget extends Widget {

    private final TerrainProjectorBlockEntity blockEntity;
    private final MapCameraController cameraController;
    private float framingSpan;
    private boolean rotating;

    public TerrainProjectorPipWidget(TerrainProjectorBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
        this.framingSpan = Math.max((blockEntity.projectionRadius * 2 + 1) * blockEntity.getModelSideLength(), 384);
        this.cameraController = new MapCameraController(new MapCameraState(
                0,
                0,
                0,
                45,
                45,
                this.framingSpan * 1.5F,
                60
        ));
    }

    @Override
    public void doRender(IGUIGraphics graphics, int mouseX, int mouseY, float a) {
        if (TerrainMapManager.INSTANCE.compatibleMode) {
            graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, 0xFF101010);
            graphics.drawCenteredString(IComponent.translatable("xklibmc.terrain_projector.compatibility_unavailable"), this.x + this.width * 0.5F, this.y + (this.height - 12.0F) * 0.5F, 0xFFFFFFFF, true, 12.0F);
            super.doRender(graphics, mouseX, mouseY, a);
            return;
        }
        var level = Minecraft.getInstance().level;
        var mapLevel = TerrainMapManager.INSTANCE.getCurrentLevelChunkStorage();
        if (graphics instanceof B3dGuiGraphics b3dGraphics && level != null && mapLevel != null
                && mapLevel.getLevel().equals(level.dimension().identifier())) {
            var nextFramingSpan = Math.max(
                    (this.blockEntity.projectionRadius * 2 + 1) * this.blockEntity.getModelSideLength(),
                    mapLevel.getMaxY() - mapLevel.getMinY()
            );
            if (nextFramingSpan != this.framingSpan) {
                this.cameraController.setDistance(this.cameraController.distance() * nextFramingSpan / this.framingSpan);
                this.framingSpan = nextFramingSpan;
            }
            this.cameraController.target().y = (mapLevel.getMinY() + mapLevel.getMaxY()) * 0.5F;
            var scaleX = XKLibBaseScreen.tryGetScaleX();
            var scaleY = XKLibBaseScreen.tryGetScaleY();
            var x0 = (int) (this.x / scaleX);
            var y0 = (int) (this.y / scaleY);
            var x1 = (int) ((this.x + this.width) / scaleX);
            var y1 = (int) ((this.y + this.height) / scaleY);
            var frame = new MapFrameSnapshot(
                    level.dimension(),
                    MapViewportPresets.WORLD_MAP,
                    this.cameraController.state(),
                    this.x,
                    this.y,
                    this.width,
                    this.height,
                    false,
                    0,
                    0,
                    mapLevel
            );
            b3dGraphics.getInner().submitPictureInPictureRenderState(new TerrainProjectorPipRenderer.TerrainProjectorState(
                    frame,
                    this.blockEntity.getNormalizedCenterPos(),
                    this.blockEntity.getModelPositions(),
                    this.blockEntity.lodLevel,
                    x0,
                    x1,
                    y0,
                    y1,
                    1.0F,
                    null,
                    new ScreenRectangle(x0, y0, x1 - x0, y1 - y0)
            ));
        }
        super.doRender(graphics, mouseX, mouseY, a);
    }

    @Override
    protected boolean onMouseClicked(IMouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 2) {
            return false;
        }
        this.rotating = true;
        return true;
    }

    @Override
    protected boolean onMouseReleased(IMouseButtonEvent event) {
        if (event.button() != 2) {
            return false;
        }
        this.rotating = false;
        return true;
    }

    @Override
    protected boolean onMouseDragged(IMouseButtonEvent event, double dx, double dy) {
        if (event.button() != 2 || !this.rotating) {
            return false;
        }
        this.cameraController.rotate(dx, dy);
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        this.cameraController.zoom(scrollY);
        return true;
    }

    @Override
    public void onFocusChanged(boolean focused) {
        if (!focused) {
            this.rotating = false;
        }
    }
}
