package com.xkball.x3dmap.client.map.selection;

import com.xkball.x3dmap.ServerConfig;
import com.xkball.x3dmap.api.client.map.WorldMapEvent;
import com.xkball.x3dmap.api.client.map.WorldMapExtension;
import com.xkball.x3dmap.api.client.map.WorldMapExtensionContext;
import com.xkball.x3dmap.api.client.map.WorldMapExtensionService;
import com.xkball.x3dmap.client.render.pip.WorldTerrainPipRenderer;
import com.xkball.x3dmap.client.terrain.TerrainChunkManager;
import com.xkball.x3dmap.network.c2s.RequestServerChunk;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklib.XKLib;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklib.ui.widget.IconButton;
import com.xkball.xklib.ui.widget.Widget;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class SelectionExtension implements WorldMapExtension {
    
    public static final String EXTENSION_ID = "selection";
    private static @Nullable SelectionStorage currentStorage;
    
    private final SelectionStorage storage = new SelectionStorage();
    private final SelectionRectangleWidget selectionRectWidget = new SelectionRectangleWidget();
    private boolean selecting;
    private boolean dragging;
    private Vector2f dragStartScreen;
    private Vector2f dragEndScreen;
    
    public static @Nullable SelectionStorage currentStorage() {
        return currentStorage;
    }
    
    @Override
    public String id() {
        return EXTENSION_ID;
    }
    
    @Override
    public int order() {
        return 1;
    }
    
    @Override
    public void init(WorldMapExtensionContext context) {
        WorldTerrainPipRenderer.regRenderLayers(SelectionOverlayRenderer::new);
    }
    
    @Override
    public List<String> enabledLayers(WorldMapExtensionService service) {
        return List.of("selection");
    }
    
    @Override
    public void onMapOpened(WorldMapExtensionService service) {
        currentStorage = this.storage;
        service.addTopBar2Widget(new IconButton(VanillaUtils.modrl("icon/section"), () -> this.selecting = !this.selecting)
                .withTooltip(IComponent.translatable("xklibmc.selection.toggle_mode")));
        service.addTopBar2Widget(new IconButton(VanillaUtils.modrl("icon/section_clear"), this::clearSelection)
                .withTooltip(IComponent.translatable("xklibmc.selection.clear_selected")));
        service.addTopBar2Widget(new IconButton(VanillaUtils.modrl("icon/section_renew"), this::clientRerender)
                .withTooltip(IComponent.translatable("xklibmc.selection.rerender_chunks")));
        service.addTopBar2Widget(new IconButton(VanillaUtils.modrl("icon/section_renew_server"), this::serverRerender)
                .withTooltip(IComponent.translatable("xklibmc.selection.request_resend")));
        service.addTopBar2Widget(new IconButton(VanillaUtils.modrl("icon/section_delete"), this::deleteSelection)
                .withTooltip(IComponent.translatable("xklibmc.selection.delete_chunks")));
        service.addTopBar2Widget(new Widget().setCSSClassName("splitter"));
        
        service.setInnerOverlayProvider(() -> this.selectionRectWidget);
        service.refreshInnerOverlay();
    }
    
    @Override
    public void onMapClosed(WorldMapExtensionService service) {
        currentStorage = null;
        this.storage.clear();
        this.selecting = false;
        this.dragging = false;
        this.selectionRectWidget.clear();
    }
    
    @Override
    public void onMapEvent(WorldMapExtensionService service, WorldMapEvent event) {
        if (!this.selecting) {
            return;
        }
        if (event instanceof WorldMapEvent.MouseClicked clicked && clicked.event().button() == 0 && !clicked.doubleClick()) {
            this.dragStartScreen = new Vector2f((float) clicked.event().x(), (float) clicked.event().y());
            this.dragging = true;
            this.selectionRectWidget.setStart(this.dragStartScreen);
            this.selectionRectWidget.setEnd(this.dragStartScreen);
            service.refreshInnerOverlay();
            clicked.consume();
        } else if (event instanceof WorldMapEvent.MouseDragged dragged && dragged.event().button() == 0 && this.dragging) {
            this.dragEndScreen = new Vector2f((float) (dragged.event().x() + dragged.dx()), (float) (dragged.event().y() + dragged.dy()));
            this.selectionRectWidget.setEnd(this.dragEndScreen);
            service.refreshInnerOverlay();
            dragged.consume();
        } else if (event instanceof WorldMapEvent.MouseReleased released && released.event().button() == 0 && this.dragging) {
            this.dragging = false;
            this.dragEndScreen = new Vector2f((float) released.event().x(), (float) released.event().y());
            this.selectionRectWidget.setEnd(this.dragEndScreen);
            service.refreshInnerOverlay();
            this.finishSelection(service);
            this.selecting = false;
            released.consume();
        }
    }
    
    private void finishSelection(WorldMapExtensionService service) {
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
                var wp = service.projScreen2World(sx, sy);
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
        service.refreshInnerOverlay();
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
