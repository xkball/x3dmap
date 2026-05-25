package com.xkball.x3dmap.client.terrain;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.logging.LogUtils;
import com.xkball.xklibmc.api.client.mixin.IExtendedGpuDevice;
import com.xkball.xklibmc.utils.ClientUtils;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TerrainTextureManager implements AutoCloseable {
    
    public static final int VIRTUAL_TEXTURE_SIZE = 16384;
    private static final int CHUNK_SIZE = 16;
    private static final int BYTES_PER_PIXEL = 4;
    private static final int CHUNKS_PER_AXIS = VIRTUAL_TEXTURE_SIZE / CHUNK_SIZE;
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static final int textureUsage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING;
    private final Map<VirtualTexturePos, VirtualTextures> texturesMap = new ConcurrentHashMap<>();
    private final LevelChunkStorage levelChunkStorage;
    
    public TerrainTextureManager(LevelChunkStorage levelChunkStorage) {
        this.levelChunkStorage = levelChunkStorage;
    }
    
    public boolean uploadChunk(ChunkStorage chunkStorage) {
        return this.uploadChunk(chunkStorage.chunkPos, chunkStorage.heightMap);
    }
    
    public boolean uploadChunk(ChunkPos chunkPos, ChunkHeightMap heightMap) {
        var uploadInfo = this.getUploadInfo(chunkPos);
        var textures = this.getOrCreateTextures(uploadInfo.texturePos());
        ByteBuffer colorUploadBuffer = null;
        ByteBuffer depthUploadBuffer = null;
        try {
            colorUploadBuffer = MemoryUtil.memAlloc(CHUNK_SIZE * CHUNK_SIZE * BYTES_PER_PIXEL);
            depthUploadBuffer = MemoryUtil.memAlloc(CHUNK_SIZE * CHUNK_SIZE * BYTES_PER_PIXEL);
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    var color = heightMap.getColor(x, z);
                    colorUploadBuffer.put((byte) ((color >> 16) & 0xFF));
                    colorUploadBuffer.put((byte) ((color >> 8) & 0xFF));
                    colorUploadBuffer.put((byte) (color & 0xFF));
                    colorUploadBuffer.put((byte) ((color >> 24) & 0xFF));
                    depthUploadBuffer.putInt(ARGB.alpha(color) == 0 ? levelChunkStorage.minHeight - 1 : heightMap.get(x, z));
                }
            }
            colorUploadBuffer.flip();
            depthUploadBuffer.flip();
            ClientUtils.getCommandEncoder().writeToTexture(
                    textures.colorTexture(), colorUploadBuffer,
                    NativeImage.Format.RGBA, 0, 0,
                    uploadInfo.destX(), uploadInfo.destY(),
                    CHUNK_SIZE, CHUNK_SIZE
            );
            ClientUtils.getCommandEncoder().writeToTexture(
                    textures.depthTexture(), depthUploadBuffer,
                    NativeImage.Format.RGBA, 0, 0,
                    uploadInfo.destX(), uploadInfo.destY(),
                    CHUNK_SIZE, CHUNK_SIZE
            );
        } finally {
            if (colorUploadBuffer != null) {
                MemoryUtil.memFree(colorUploadBuffer);
            }
            if (depthUploadBuffer != null) {
                MemoryUtil.memFree(depthUploadBuffer);
            }
        }
        return true;
    }
    
    
    public VirtualTextures getTextures(ChunkPos chunkPos) {
        return this.getOrCreateTextures(this.getTexturePos(chunkPos));
    }
    
    public VirtualTextures getTextures(VirtualTexturePos pos) {
        return this.getOrCreateTextures(pos);
    }
    
    private VirtualTextures getOrCreateTextures(VirtualTexturePos materialPos) {
        return this.texturesMap.computeIfAbsent(materialPos, this::createTextures);
    }
    
    private VirtualTextures createTextures(VirtualTexturePos materialPos) {
        @SuppressWarnings({"removal", "UnstableApiUsage"})
        var device = IExtendedGpuDevice.cast(ClientUtils.getGpuDevice().backend);
        var colorTexture = device.xklib$createSparseTexture(
                "terrain_virtual_color_" + materialPos.x() + "_" + materialPos.z(),
                textureUsage, TextureFormat.RGBA8, VIRTUAL_TEXTURE_SIZE, VIRTUAL_TEXTURE_SIZE, 1, 0
        );
        var depthTexture = device.xklib$createSparseTexture(
                "terrain_virtual_depth_" + materialPos.x() + "_" + materialPos.z(),
                textureUsage, TextureFormat.RGBA8, VIRTUAL_TEXTURE_SIZE, VIRTUAL_TEXTURE_SIZE, 1, levelChunkStorage.minHeight - 1
        );
        LOGGER.info("Created virtual terrain textures for material ({},{})", materialPos.x(), materialPos.z());
        return new VirtualTextures(colorTexture, ClientUtils.getGpuDevice().createTextureView(colorTexture), depthTexture, ClientUtils.getGpuDevice().createTextureView(depthTexture));
    }
    
    private VirtualTexturePos getTexturePos(ChunkPos chunkPos) {
        return new VirtualTexturePos(
                Math.floorDiv(chunkPos.x(), CHUNKS_PER_AXIS),
                Math.floorDiv(chunkPos.z(), CHUNKS_PER_AXIS)
        );
    }
    
    public ChunkUploadInfo getUploadInfo(ChunkPos chunkPos) {
        var materialPos = this.getTexturePos(chunkPos);
        var localChunkX = Math.floorMod(chunkPos.x(), CHUNKS_PER_AXIS);
        var localChunkZ = Math.floorMod(chunkPos.z(), CHUNKS_PER_AXIS);
        return new ChunkUploadInfo(materialPos, localChunkX * CHUNK_SIZE, localChunkZ * CHUNK_SIZE);
    }
    
    public void clearChunk(ChunkPos chunkPos) {
        var uploadInfo = this.getUploadInfo(chunkPos);
        var textures = this.getTextures(uploadInfo.texturePos());
        var clearColor = MemoryUtil.memAlloc(CHUNK_SIZE * CHUNK_SIZE * BYTES_PER_PIXEL);
        var clearDepth = MemoryUtil.memAlloc(CHUNK_SIZE * CHUNK_SIZE * BYTES_PER_PIXEL);
        try {
            for (int i = 0; i < CHUNK_SIZE * CHUNK_SIZE; i++) {
                clearColor.putInt(0);
                clearDepth.putInt(levelChunkStorage.minHeight - 1);
            }
            clearColor.flip();
            clearDepth.flip();
            ClientUtils.getCommandEncoder().writeToTexture(
                    textures.colorTexture(), clearColor,
                    NativeImage.Format.RGBA, 0, 0,
                    uploadInfo.destX(), uploadInfo.destY(),
                    CHUNK_SIZE, CHUNK_SIZE
            );
            ClientUtils.getCommandEncoder().writeToTexture(
                    textures.depthTexture(), clearDepth,
                    NativeImage.Format.RGBA, 0, 0,
                    uploadInfo.destX(), uploadInfo.destY(),
                    CHUNK_SIZE, CHUNK_SIZE
            );
        } finally {
            MemoryUtil.memFree(clearColor);
            MemoryUtil.memFree(clearDepth);
        }
    }
    
    public void clear() {
        for (var textures : this.texturesMap.values()) {
            textures.colorTextureView().close();
            textures.depthTextureView().close();
            textures.colorTexture().close();
            textures.depthTexture().close();
        }
        this.texturesMap.clear();
    }
    
    @Override
    public void close() {
        this.clear();
    }
    
    public record VirtualTextures(GpuTexture colorTexture, GpuTextureView colorTextureView, GpuTexture depthTexture,
                                  GpuTextureView depthTextureView) {
    }
    
    public record VirtualTexturePos(int x, int z) {
    }
    
    public record ChunkUploadInfo(VirtualTexturePos texturePos, int destX, int destY) {
    }
    
}
