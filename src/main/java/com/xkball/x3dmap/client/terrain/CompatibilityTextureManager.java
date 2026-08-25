package com.xkball.x3dmap.client.terrain;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.X3dMapClient;
import com.xkball.x3dmap.client.terrain.file.MapLevel;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@NonNullByDefault
public final class CompatibilityTextureManager implements AutoCloseable {

    public static final int TEXTURE_SIDE_LENGTH = 1024;
    public static final int BLOCK_SIDE_LENGTH = TEXTURE_SIDE_LENGTH * 16;
    private static final Logger LOGGER = LogUtils.getLogger();
    private final MapLevel mapLevel;
    private final Map<TexturePos, TextureEntry> textures = new HashMap<>();
    private final Set<TexturePos> loading = new HashSet<>();
    private final Map<TexturePos, Map<Integer, Integer>> pendingUpdates = new HashMap<>();
    private boolean closed;

    public CompatibilityTextureManager(MapLevel mapLevel) {
        this.mapLevel = mapLevel;
    }

    public Set<TexturePos> getTexturePositions() {
        var result = new HashSet<TexturePos>();
        for (var regionPos : this.mapLevel.getRegions()) {
            result.add(TexturePos.ofRegion(regionPos));
        }
        return result;
    }

    public @Nullable Identifier getOrLoad(TexturePos pos) {
        var entry = this.textures.get(pos);
        if (entry != null) return entry.id();
        if (this.closed || !this.loading.add(pos)) return null;
        this.mapLevel.loadCompatibilityTextureColors(pos.x(), pos.z())
                .thenApplyAsync(CompatibilityTextureManager::createImage, X3dMapClient.taskExecutor)
                .whenCompleteAsync((image, throwable) -> {
                    if (throwable != null || image == null) {
                        this.loading.remove(pos);
                        this.pendingUpdates.remove(pos);
                        LOGGER.error("Failed to load compatibility terrain texture at {}", pos, throwable);
                        return;
                    }
                    this.register(pos, image);
                }, X3dMapClient.mainThreadExecutor);
        return null;
    }

    public void updateChunk(ChunkPos chunkPos, int color) {
        var texturePos = TexturePos.ofChunk(chunkPos);
        var pixelX = Math.floorMod(chunkPos.x(), TEXTURE_SIDE_LENGTH);
        var pixelZ = Math.floorMod(chunkPos.z(), TEXTURE_SIDE_LENGTH);
        X3dMapClient.mainThreadExecutor.execute(() -> this.updatePixel(texturePos, pixelX, pixelZ, color));
    }

    private void register(TexturePos pos, NativeImage image) {
        if (this.closed) {
            image.close();
            this.loading.remove(pos);
            this.pendingUpdates.remove(pos);
            return;
        }
        var updates = this.pendingUpdates.remove(pos);
        if (updates != null) {
            updates.forEach((index, color) -> image.setPixel(index % TEXTURE_SIDE_LENGTH, index / TEXTURE_SIDE_LENGTH, color));
        }
        DynamicTexture texture = null;
        try {
            var id = pos.identifier();
            texture = new DynamicTexture(() -> "x3dmap compatibility terrain " + pos, image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            this.textures.put(pos, new TextureEntry(id, texture));
        } catch (RuntimeException exception) {
            if (texture == null) {
                image.close();
            } else {
                texture.close();
            }
            LOGGER.error("Failed to register compatibility terrain texture at {}", pos, exception);
        } finally {
            this.loading.remove(pos);
        }
    }

    private void updatePixel(TexturePos pos, int pixelX, int pixelZ, int color) {
        if (this.closed) return;
        var entry = this.textures.get(pos);
        if (entry == null) {
            if (this.loading.contains(pos)) {
                this.pendingUpdates.computeIfAbsent(pos, ignored -> new HashMap<>())
                        .put(pixelZ * TEXTURE_SIDE_LENGTH + pixelX, color);
            }
            return;
        }
        try {
            entry.texture().getPixels().setPixel(pixelX, pixelZ, color);
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                    entry.texture().getTexture(), entry.texture().getPixels(), 0, 0,
                    pixelX, pixelZ, 1, 1, pixelX, pixelZ);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to update compatibility terrain texture at {}", pos, exception);
        }
    }

    private static NativeImage createImage(int[] colors) {
        var image = new NativeImage(TEXTURE_SIDE_LENGTH, TEXTURE_SIDE_LENGTH, true);
        try {
            for (var z = 0; z < TEXTURE_SIDE_LENGTH; z++) {
                for (var x = 0; x < TEXTURE_SIDE_LENGTH; x++) {
                    image.setPixel(x, z, colors[z * TEXTURE_SIDE_LENGTH + x]);
                }
            }
            return image;
        } catch (RuntimeException exception) {
            image.close();
            throw exception;
        }
    }

    @Override
    public void close() {
        this.closed = true;
        this.loading.clear();
        this.pendingUpdates.clear();
        for (var entry : this.textures.values()) {
            Minecraft.getInstance().getTextureManager().release(entry.id());
        }
        this.textures.clear();
    }

    public record TexturePos(int x, int z) {

        public static TexturePos ofRegion(RegionPos pos) {
            return new TexturePos(
                    Math.floorDiv(pos.x(), TEXTURE_SIDE_LENGTH / RegionPos.REGION_SIZE),
                    Math.floorDiv(pos.z(), TEXTURE_SIDE_LENGTH / RegionPos.REGION_SIZE));
        }

        public static TexturePos ofChunk(ChunkPos pos) {
            return new TexturePos(
                    Math.floorDiv(pos.x(), TEXTURE_SIDE_LENGTH),
                    Math.floorDiv(pos.z(), TEXTURE_SIDE_LENGTH));
        }

        public int minBlockX() {
            return this.x * BLOCK_SIDE_LENGTH;
        }

        public int minBlockZ() {
            return this.z * BLOCK_SIDE_LENGTH;
        }

        private Identifier identifier() {
            return VanillaUtils.modRL("compatibility/terrain/" + this.x + "_" + this.z);
        }
    }

    private record TextureEntry(Identifier id, DynamicTexture texture) {
    }
}
