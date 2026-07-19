package com.xkball.x3dmap.client.map.selection;

import com.xkball.x3dmap.ServerConfig;
import com.xkball.x3dmap.api.client.gui.IMapScreenContext;
import com.xkball.x3dmap.api.client.gui.IMapScreenExtension;
import com.xkball.x3dmap.api.client.gui.MapToolbarSlot;
import com.xkball.x3dmap.api.client.gui.input.MapInputEvent;
import com.xkball.x3dmap.client.terrain.TerrainChunkManager;
import com.xkball.x3dmap.network.c2s.RequestServerChunk;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklib.XKLib;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.widget.IconButton;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;

@NonNullByDefault
public class SelectionExtension implements IMapScreenExtension {
    
    private static @Nullable SelectionStorage currentStorage;
    
    private final IMapScreenContext context;
    private final SelectionStorage storage = new SelectionStorage();
    private final SelectionRectangleWidget selectionRectWidget = new SelectionRectangleWidget();
    private boolean selecting;
    private boolean dragging;
    private @Nullable Vector2f dragStartScreen;
    private @Nullable Vector2f dragEndScreen;

    public SelectionExtension(IMapScreenContext context) {
        this.context = context;
    }
    
    public static @Nullable SelectionStorage currentStorage() {
        return currentStorage;
    }
    
    @Override
    public void onOpen() {
        currentStorage = this.storage;
        this.context.gui().addToolbarWidget(MapToolbarSlot.TOP_SECONDARY, new IconButton(VanillaUtils.modrl("icon/section"), () -> this.selecting = !this.selecting)
                .withTooltip(IComponent.translatable("xklibmc.selection.toggle_mode")));
        this.context.gui().addToolbarWidget(MapToolbarSlot.TOP_SECONDARY, new IconButton(VanillaUtils.modrl("icon/section_clear"), this::clearSelection)
                .withTooltip(IComponent.translatable("xklibmc.selection.clear_selected")));
        this.context.gui().addToolbarWidget(MapToolbarSlot.TOP_SECONDARY, new IconButton(VanillaUtils.modrl("icon/section_renew"), this::clientRerender)
                .withTooltip(IComponent.translatable("xklibmc.selection.rerender_chunks")));
        if(ServerConfig.ALLOW_SERVER_SENT_CHUNK.get() || XKLib.IS_DEBUG){
            this.context.gui().addToolbarWidget(MapToolbarSlot.TOP_SECONDARY, new IconButton(VanillaUtils.modrl("icon/section_renew_server"), this::serverRerender)
                    .withTooltip(IComponent.translatable("xklibmc.selection.request_resend")));
        }
        this.context.gui().addToolbarWidget(MapToolbarSlot.TOP_SECONDARY, new IconButton(VanillaUtils.modrl("icon/section_delete"), this::deleteSelection)
                .withTooltip(IComponent.translatable("xklibmc.selection.delete_chunks")));
        this.context.gui().addToolbarWidget(MapToolbarSlot.TOP_SECONDARY, new Widget().setCSSClassName("splitter"));
        
        this.context.gui().setOverlay(this.context.extensionId(), () -> this.selectionRectWidget);
        this.context.gui().refreshOverlays();
    }
    
    @Override
    public void close() {
        currentStorage = null;
        this.storage.clear();
        this.selecting = false;
        this.dragging = false;
        this.selectionRectWidget.clear();
    }
    
    @Override
    public boolean handle(MapInputEvent event) {
        if (!this.selecting) {
            return false;
        }
        switch (event) {
            case MapInputEvent.MouseClicked clicked when clicked.event().button() == 0 && !clicked.doubleClick() -> {
                this.dragStartScreen = new Vector2f((float) clicked.event().x(), (float) clicked.event().y());
                this.dragging = true;
                this.selectionRectWidget.setStart(this.dragStartScreen);
                this.selectionRectWidget.setEnd(this.dragStartScreen);
                this.context.gui().refreshOverlays();
                return true;
            }
            case MapInputEvent.MouseDragged dragged when dragged.event().button() == 0 && this.dragging -> {
                this.dragEndScreen = new Vector2f((float) (dragged.event().x() + dragged.dx()), (float) (dragged.event().y() + dragged.dy()));
                this.selectionRectWidget.setEnd(this.dragEndScreen);
                this.context.gui().refreshOverlays();
                return true;
            }
            case MapInputEvent.MouseReleased released when released.event().button() == 0 && this.dragging -> {
                this.dragging = false;
                this.dragEndScreen = new Vector2f((float) released.event().x(), (float) released.event().y());
                this.selectionRectWidget.setEnd(this.dragEndScreen);
                this.context.gui().refreshOverlays();
                this.finishSelection();
                this.selecting = false;
                return true;
            }
            default -> {
            }
        }
        return false;
    }
    
    private void finishSelection() {
        if (this.dragStartScreen == null || this.dragEndScreen == null) {
            return;
        }
        var x0 = Math.min(this.dragStartScreen.x, this.dragEndScreen.x);
        var y0 = Math.min(this.dragStartScreen.y, this.dragEndScreen.y);
        var x1 = Math.max(this.dragStartScreen.x, this.dragEndScreen.x);
        var y1 = Math.max(this.dragStartScreen.y, this.dragEndScreen.y);
        
        var worldMin = new Vector3f(Float.MAX_VALUE, 0, Float.MAX_VALUE);
        var worldMax = new Vector3f(-Float.MAX_VALUE, 0, -Float.MAX_VALUE);
        var samples = 0;
        var step = 16f;
        for (var sx = x0; sx <= x1; sx += step) {
            for (var sy = y0; sy <= y1; sy += step) {
                var wp = this.context.view().screenToWorld(sx, sy);
                if (wp != null) {
                    worldMin.x = Math.min(worldMin.x, wp.x);
                    worldMin.z = Math.min(worldMin.z, wp.z);
                    worldMax.x = Math.max(worldMax.x, wp.x);
                    worldMax.z = Math.max(worldMax.z, wp.z);
                    samples++;
                }
            }
        }
        if (samples == 0) {
            this.selectionRectWidget.clear();
            return;
        }
        
        var minChunkX = (int) Math.floor(worldMin.x) >> 4;
        var minChunkZ = (int) Math.floor(worldMin.z) >> 4;
        var maxChunkX = (int) Math.floor(worldMax.x) >> 4;
        var maxChunkZ = (int) Math.floor(worldMax.z) >> 4;
        
        var toAdd = new HashSet<ChunkPos>();
        for (var cx = minChunkX; cx <= maxChunkX; cx++) {
            for (var cz = minChunkZ; cz <= maxChunkZ; cz++) {
                toAdd.add(new ChunkPos(cx, cz));
            }
        }
        
        this.storage.addAll(toAdd);
        this.selectionRectWidget.clear();
        this.context.gui().refreshOverlays();
    }
    
    private void clearSelection() {
        this.selectionRectWidget.clear();
        this.storage.clear();
    }
    
    private void clientRerender() {
        for (var chunkPos : this.storage.selectedChunks()) {
            TerrainChunkManager.INSTANCE.submitUpdate(chunkPos, true);
        }
        this.clearSelection();
    }
    
    private void serverRerender() {
        if (!ServerConfig.ALLOW_SERVER_SENT_CHUNK.get() && !XKLib.IS_DEBUG) {
            return;
        }
        var list = new ArrayList<>(this.storage.selectedChunks());
        if (!list.isEmpty()) {
            ClientPacketDistributor.sendToServer(new RequestServerChunk(list, true));
        }
        this.clearSelection();
    }
    
    private void deleteSelection() {
        var levelStorage = TerrainChunkManager.INSTANCE.getCurrentLevelChunkStorage();
        if (levelStorage != null) {
            for (var chunkPos : this.storage.selectedChunks()) {
                levelStorage.deleteChunk(chunkPos);
            }
        }
        this.clearSelection();
    }
}
