package com.xkball.x3dmap.client.map.minimap;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.xkball.x3dmap.ClientConfig;
import com.xkball.x3dmap.api.client.render.Map2dLayerPhase;
import com.xkball.x3dmap.api.client.render.MapViewportPresets;
import com.xkball.x3dmap.api.client.viewport.MapCameraState;
import com.xkball.x3dmap.client.map.render.Map2dLayerRenderer;
import com.xkball.x3dmap.client.map.render.Map2dRenderContextImpl;
import com.xkball.x3dmap.client.map.viewport.MapFrameSnapshot;
import com.xkball.x3dmap.client.map.viewport.MapRenderViewport;
import com.xkball.x3dmap.client.render.pip.WorldTerrainPipRenderer;
import com.xkball.x3dmap.client.terrain.TerrainChunkManager;
import com.xkball.xklib.ui.css.property.value.CssLengthUnit;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.api.client.b3d.SamplerCacheCache;
import com.xkball.xklibmc.utils.ClientUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.Lazy;
import org.jspecify.annotations.Nullable;

@NonNullByDefault
public final class MinimapHudRenderer {

    private static final Lazy<WorldTerrainPipRenderer> TERRAIN_RENDERER = Lazy.of(() -> new WorldTerrainPipRenderer(Minecraft.getInstance().renderBuffers().bufferSource()));
    private static @Nullable MapRenderViewport viewport;
    private static @Nullable GpuTexture offscreenColorTexture;
    private static @Nullable GpuTextureView offscreenColorTextureView;
    private static @Nullable GpuTexture offscreenDepthTexture;
    private static @Nullable GpuTextureView offscreenDepthTextureView;
    private static int offscreenTexWidth;
    private static int offscreenTexHeight;

    private static long frameCount;
    private static double lastRenderPlayerX = Double.NaN;
    private static double lastRenderPlayerZ = Double.NaN;
    private static @Nullable Level lastLevel;

    private MinimapHudRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || mc.screen != null) return;
        if (!ClientConfig.MINIMAP_ENABLED.get()) return;
        if (mc.level != lastLevel) {
            if (viewport != null) {
                viewport.close();
            }
            viewport = new MapRenderViewport(
                    TerrainChunkManager.INSTANCE.mapPluginRegistry.runtime(),
                    new Widget(),
                    mc.level.dimension(),
                    MapViewportPresets.MINIMAP,
                    new MapCameraState(0, 0, 0, 89, 0, 0, 60)
            );
            lastLevel = mc.level;
            frameCount = 0;
            lastRenderPlayerX = Double.NaN;
            lastRenderPlayerZ = Double.NaN;
        }
        frameCount++;
        var scale = CssLengthUnit.rpxScaleWorkaround;
        var window = mc.getWindow();
        var guiScaledHeight = window.getGuiScaledHeight();
        var guiScaledWidth = window.getGuiScaledWidth();
        float sizePercent = ClientConfig.MINIMAP_SIZE.get() / 100.0f;
        float paddingPercent = ClientConfig.MINIMAP_PADDING.get() / 100.0f;
        int mapSize = (int) (guiScaledHeight * sizePercent);
        int padding = (int) (guiScaledHeight * paddingPercent);
        int x0 = guiScaledWidth - mapSize - padding;
        int y0 = padding;
        int x1 = x0 + mapSize;
        int y1 = y0 + mapSize;
        var realMapSize = mapSize * scale;
        int newTexSize = (int) (realMapSize * 1.1f);
        if (newTexSize < 1) newTexSize = 1;

        boolean needsResize = offscreenColorTexture == null || offscreenTexWidth != newTexSize || offscreenTexHeight != newTexSize;
        if (needsResize) {
            releaseOffscreenTextures();
            createOffscreenTextures(newTexSize, newTexSize);
            offscreenTexWidth = newTexSize;
            offscreenTexHeight = newTexSize;
        }

        var player = mc.player;
        var blockPos = player.blockPosition();
        var targetY = MinimapRenderHelper.getCameraTargetY(player.getY(), blockPos.getX(), blockPos.getZ(), mc.level.getMinY());
        var rotateWithPlayer = ClientConfig.MINIMAP_ROTATE_WITH_PLAYER.get();
        var yRot = rotateWithPlayer ? MinimapPlayerMarker.mapYawForPlayerUp(player.getYRot()) : 0.0f;
        var camera = new MapCameraState(
                (float) player.getX(),
                targetY,
                (float) player.getZ(),
                ClientConfig.MINIMAP_CAMERA_X_ROT.get().floatValue(),
                yRot,
                ClientConfig.MINIMAP_CAMERA_LENGTH.get().floatValue(),
                ClientConfig.MINIMAP_CAMERA_FOV.get().floatValue()
        );
        if (viewport != null) {
            viewport.tick();
        }

        int renderInterval = ClientConfig.MINIMAP_RENDER_INTERVAL.get();
        boolean shouldRender = ((frameCount - 1) % renderInterval == 0) || needsResize || viewport != null && viewport.invalidated();
//        shouldRender = true;
        if (shouldRender && offscreenColorTextureView != null && offscreenDepthTextureView != null && viewport != null) {
            renderTerrainToOffscreen(viewport, camera, newTexSize);
            lastRenderPlayerX = player.getX();
            lastRenderPlayerZ = player.getZ();
        }

        if (offscreenColorTextureView != null) {
            float u0, v0, u1, v1;
            if (!Double.isNaN(lastRenderPlayerX)) {
                float dx = (float) (player.getX() - lastRenderPlayerX);
                float dz = (float) (player.getZ() - lastRenderPlayerZ);

                float cameraLength = ClientConfig.MINIMAP_CAMERA_LENGTH.get().floatValue();
                float fov = ClientConfig.MINIMAP_CAMERA_FOV.get().floatValue();
                float worldCoverage = (float) (2 * (cameraLength + 100) * Math.tan(Math.toRadians(fov / 2)));
                float pixelsPerWorld = realMapSize / worldCoverage;

                int texCenterX = offscreenTexWidth / 2;
                int texCenterY = offscreenTexHeight / 2;
                float mapHalfW = realMapSize / 2;
                float mapHalfH = realMapSize / 2;

                float offsetX = (int) (-dx * pixelsPerWorld);
                float offsetZ = (int) (-dz * pixelsPerWorld);
                float maxOffset = (offscreenTexWidth - realMapSize) / 2;
                offsetX = Math.clamp(offsetX, -maxOffset, maxOffset);
                offsetZ = Math.clamp(offsetZ, -maxOffset, maxOffset);

                float srcX = texCenterX - mapHalfW - offsetX;
                float srcY = texCenterY - mapHalfH - offsetZ;

                u0 = srcX / offscreenTexWidth;
                v0 = 1 - srcY / offscreenTexHeight;
                u1 = (srcX + realMapSize) / offscreenTexWidth;
                v1 = 1 - (srcY + realMapSize) / offscreenTexHeight;
            } else {
                float margin = (offscreenTexWidth - realMapSize) / (2f * offscreenTexWidth);
                u0 = margin;
                v0 = margin;
                u1 = 1f - margin;
                v1 = 1f - margin;
            }

            graphics.blit(offscreenColorTextureView, SamplerCacheCache.NEAREST_CLAMP, x0, y0, x1, y1, u0, u1, v0, v1);
        }

        var b3dGraphics = MinimapRenderHelper.getOrCreateB3dGuiGraphics(graphics);
        if (viewport != null) {
            var displayFrame = new MapFrameSnapshot(
                    mc.level.dimension(),
                    MapViewportPresets.MINIMAP,
                    camera,
                    x0,
                    y0,
                    mapSize,
                    mapSize,
                    false,
                    512,
                    ClientConfig.MINIMAP_HIGH_DETAIL_RANGE.get(),
                    mc.level.getMinY(),
                    TerrainChunkManager.INSTANCE.getCurrentLevelChunkStorage()
            );
            var displayLayers = viewport.prepare2d(displayFrame);
            var context = new Map2dRenderContextImpl(displayFrame, b3dGraphics);
            Map2dLayerRenderer.render(displayLayers, Map2dLayerPhase.CONTENT, context);
            MinimapRenderHelper.drawBorder(graphics, x0, y0, x1, y1);
            CompassRenderer.render(b3dGraphics, x0, y0, x1, y1, yRot, 0, 8f);
            MinimapPlayerMarker.render(b3dGraphics, x0, y0, x1, y1, player.getYRot(), rotateWithPlayer);
            Map2dLayerRenderer.render(displayLayers, Map2dLayerPhase.FOREGROUND, context);
        } else {
            MinimapRenderHelper.drawBorder(graphics, x0, y0, x1, y1);
            CompassRenderer.render(b3dGraphics, x0, y0, x1, y1, yRot, 0, 8f);
            MinimapPlayerMarker.render(b3dGraphics, x0, y0, x1, y1, player.getYRot(), rotateWithPlayer);
        }
        var coords = "%d %d %d".formatted(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        var textX = (x0 + x1) / 2f;
        var textY = y1 + 8;
        var font = Minecraft.getInstance().font;
        var cw = font.width(coords);
        var ch = font.lineHeight;
        graphics.fill((int) (textX - cw / 2f - 1), textY - 1, (int) (textX + cw / 2f + 1), textY + ch + 1, 0x90505050);
        b3dGraphics.drawCenteredString(coords, textX, textY, 0xCCFFFFFF);
    }

    private static void renderTerrainToOffscreen(MapRenderViewport viewport, MapCameraState camera, int texSize) {

        float baseFov = ClientConfig.MINIMAP_CAMERA_FOV.get().floatValue();
        float offscreenScale = texSize / (texSize / 1.1f);
        float offscreenFov = (float) (2 * Math.toDegrees(
                Math.atan(Math.tan(Math.toRadians(baseFov / 2)) * offscreenScale)
        ));

        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        var offscreenCamera = new MapCameraState(
                camera.targetX(),
                camera.targetY(),
                camera.targetZ(),
                camera.xRotation(),
                camera.yRotation(),
                camera.distance(),
                offscreenFov
        );
        var frame = new MapFrameSnapshot(
                level.dimension(),
                MapViewportPresets.MINIMAP,
                offscreenCamera,
                0,
                0,
                texSize,
                texSize,
                false,
                512,
                ClientConfig.MINIMAP_HIGH_DETAIL_RANGE.get(),
                level.getMinY(),
                TerrainChunkManager.INSTANCE.getCurrentLevelChunkStorage()
        );
        var state = new WorldTerrainPipRenderer.WorldTerrainState(
                frame,
                viewport.prepare3d(frame),
                0, texSize, 0, texSize,
                1.0f,
                null,
                new ScreenRectangle(0, 0, texSize, texSize)
        );
        var overrideTextureOld = RenderSystem.outputColorTextureOverride;
        var overrideDepthOld = RenderSystem.outputDepthTextureOverride;
        try {
            RenderSystem.outputColorTextureOverride = offscreenColorTextureView;
            RenderSystem.outputDepthTextureOverride = offscreenDepthTextureView;
            TERRAIN_RENDERER.get().renderToExternalTexture(state, offscreenColorTextureView, offscreenDepthTextureView);
        } finally {
            RenderSystem.outputColorTextureOverride = overrideTextureOld;
            RenderSystem.outputDepthTextureOverride = overrideDepthOld;
            TERRAIN_RENDERER.get().getBufferSource().endBatch();
        }
    }

    private static void createOffscreenTextures(int width, int height) {
        var device = ClientUtils.getGpuDevice();
        int usage = GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
        offscreenColorTexture = device.createTexture(
                () -> "minimap offscreen color",
                usage,
                TextureFormat.RGBA8,
                width, height, 1, 1
        );
        offscreenColorTextureView = device.createTextureView(offscreenColorTexture);

        offscreenDepthTexture = device.createTexture(
                () -> "minimap offscreen depth",
                usage,
                TextureFormat.DEPTH32,
                width, height, 1, 1
        );
        offscreenDepthTextureView = device.createTextureView(offscreenDepthTexture);
    }

    private static void releaseOffscreenTextures() {
        if (offscreenColorTextureView != null) {
            offscreenColorTextureView.close();
            offscreenColorTextureView = null;
        }
        if (offscreenColorTexture != null) {
            offscreenColorTexture.close();
            offscreenColorTexture = null;
        }
        if (offscreenDepthTextureView != null) {
            offscreenDepthTextureView.close();
            offscreenDepthTextureView = null;
        }
        if (offscreenDepthTexture != null) {
            offscreenDepthTexture.close();
            offscreenDepthTexture = null;
        }
    }
}
