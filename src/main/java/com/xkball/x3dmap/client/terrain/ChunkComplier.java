package com.xkball.x3dmap.client.terrain;

import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.client.TextureSpriteAvgColorCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@NonNullByDefault
public class ChunkComplier {
    
    private static final TextureSpriteAvgColorCache textureColorCache = new TextureSpriteAvgColorCache();
    private static final Map<BlockState, Identifier> BLOCK_STATE_OVERRIDE = Map.of(
            Blocks.GRASS_BLOCK.defaultBlockState().setValue(BlockStateProperties.SNOWY, true), Identifier.parse("block/snow")
    );
    private static final Map<Block, Identifier> BLOCK_SPRITE_OVERRIDE = Map.of(
            Blocks.GRASS_BLOCK, Identifier.parse("block/grass_block_top"),
            Blocks.SNOW, Identifier.parse("block/snow")
    );
    private static final Set<Block> IGNORED_BLOCKS = new HashSet<>(Set.of(
            Blocks.SHORT_DRY_GRASS,
            Blocks.SHORT_GRASS,
            Blocks.TALL_GRASS,
            Blocks.TALL_DRY_GRASS,
            Blocks.FERN,
            Blocks.LARGE_FERN,
            Blocks.BUSH,
            Blocks.DEAD_BUSH,
            Blocks.FIREFLY_BUSH
    ));
    
    public @Nullable ChunkStorage compile(LevelChunkStorage storage, ClientLevel level, ChunkPos chunkPos) {
        if (level.getChunk(chunkPos.x(), chunkPos.z(), ChunkStatus.FULL, false) == null) return null;
        return compile(storage, level, level.getChunk(chunkPos.x(), chunkPos.z()), chunkPos, false);
    }
    
    public @Nullable ChunkStorage compile(LevelChunkStorage storage, ClientLevel level, ChunkAccess chunk, ChunkPos chunkPos, boolean calcuColor) {
        var chunkStorage = new ChunkStorage(chunkPos, storage);
        var directions = VanillaUtils.DIRECTIONS;
        var mc = Minecraft.getInstance();
        var modelManager = mc.getModelManager().getBlockStateModelSet();
        var result = new ArrayList<ABlock.ABlockData>();
        var pos = new BlockPos(0, 0, 0).mutable();
        var chunkMinY = level.getMinY();
        var chunkMaxY = level.getMaxY();
        var heightMap = new ChunkHeightMap();
        var context = new ComplierContext(level, chunk, chunkPos, calcuColor);
        for (int px = chunkPos.getMinBlockX(); px <= chunkPos.getMaxBlockX(); px++) {
            for (int pz = chunkPos.getMinBlockZ(); pz <= chunkPos.getMaxBlockZ(); pz++) {
                var hMax = context.getHeight(Heightmap.Types.WORLD_SURFACE, px, pz) + 1;
                var hMin = context.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz) - 1;
                var hm = hMax - 1;
                while (context.getBlockState(pos.set(px, hm, pz)).isAir() && hm > chunkMinY) {
                    hm -= 1;
                }
                heightMap.set(px, pz, hm);
                pos.set(px, hm, pz);
                var bs_ = context.getBlockState(pos);
                heightMap.setColor(px, pz, processBlockColor(context, bs_, pos, modelManager));
                var h1 = context.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px + 1, pz) - 1;
                if (h1 > level.getMinY()) hMin = Math.min(hMin, h1);
                var h2 = context.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px - 1, pz) - 1;
                if (h2 > level.getMinY()) hMin = Math.min(hMin, h2);
                var h3 = context.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz + 1) - 1;
                if (h3 > level.getMinY()) hMin = Math.min(hMin, h3);
                var h4 = context.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz - 1) - 1;
                if (h4 > level.getMinY()) hMin = Math.min(hMin, h4);
                hMin = Math.clamp(hMin, level.getMinY(), hMax);
                chunkMinY = Math.min(chunkMinY, hMin);
                chunkMaxY = Math.max(chunkMaxY, hMax);
                for (int y = hMin; y < hMax; y++) {
                    pos.set(px, y, pz);
                    var bs = context.getBlockState(pos);
                    if (bs.isAir()) {
                        continue;
                    }
                    var allBlock = true;
                    int mask = 0;
                    for (var dir : directions) {
                        var b = pos.move(dir);
                        if (context.getBlockState(b).isAir()) {
                            allBlock = false;
                            mask = mask | (1 << dir.get3DDataValue());
                        }
                        pos.move(dir, -1);
                    }
                    if (allBlock) {
                        continue;
                    }
                    var color = processBlockColor(context, bs, pos, modelManager);
                    result.add(new ABlock.ABlockData(px, y, pz, color, mask));
                }
            }
        }
        chunkStorage.aabb = new AABB(chunkPos.getMinBlockX(), chunkMinY, chunkPos.getMinBlockZ(), chunkPos.getMaxBlockX(), chunkMaxY, chunkPos.getMaxBlockZ());
        chunkStorage.writeData(result);
        chunkStorage.heightMap = heightMap;
        return chunkStorage;
    }
    
    private static int processBlockColor(ComplierContext context, BlockState bs, BlockPos pos, BlockStateModelSet modelManager) {
        var model = modelManager.get(bs);
        TextureAtlasSprite sprite;
        var rl = BLOCK_STATE_OVERRIDE.get(bs);
        if (rl == null) rl = BLOCK_SPRITE_OVERRIDE.get(bs.getBlock());
        if (rl != null) {
            sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(rl);
        } else {
            sprite = model.particleMaterial(context.level, pos, bs).sprite();
        }
        if ("minecraft:missingno".equals(sprite.contents().name().toString())) return 0;
        var color = textureColorCache.getAvgColor(sprite);
        return VanillaUtils.mulColor(color, context.getBlockColor(pos, bs));
    }
    
    public record ComplierContext(ClientLevel level, ChunkAccess chunkAccess, ChunkPos chunkPos, boolean calcuColor) {
        
        public boolean insideChunk(BlockPos pos) {
            return insideChunk(pos.getX(), pos.getZ());
        }
        
        public boolean insideChunk(int x, int z) {
            return chunkPos.getMinBlockX() <= x && chunkPos.getMaxBlockX() >= x && chunkPos.getMinBlockZ() <= z && chunkPos.getMaxBlockZ() >= z;
        }
        
        public int getHeight(Heightmap.Types type, int x, int z) {
            if (insideChunk(x, z)) {
                return chunkAccess.getHeight(type, x, z);
            }
            return level.getHeight(type, x, z);
        }
        
        public BlockState getBlockState(BlockPos pos) {
            BlockState result;
            if (insideChunk(pos)) {
                result = chunkAccess.getBlockState(pos);
            } else result = level.getBlockState(pos);
            if (IGNORED_BLOCKS.contains(result.getBlock())) return Blocks.AIR.defaultBlockState();
            return result;
        }
        
        public int getBlockColor(BlockPos pos, BlockState state) {
            var mc = Minecraft.getInstance();
            var tintSource = mc.getBlockColors().getTintSources(state);
            if (tintSource.isEmpty()) return -1;
            var blockAndTintGetter = calcuColor ? new BlockAndTintGetter() {
                @Override
                public CardinalLighting cardinalLighting() {
                    return level.cardinalLighting();
                }
                
                @Override
                public int getBlockTint(BlockPos pos, ColorResolver color) {
                    if (insideChunk(pos)) {
                        return color.getColor(chunkAccess.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2).value(), pos.getX(), pos.getZ());
                    }
                    return level.getBlockTint(pos, color);
                }
                
                @Override
                public LevelLightEngine getLightEngine() {
                    return level.getLightEngine();
                }
                
                @Override
                public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
                    return level.getBlockEntity(pos);
                }
                
                @Override
                public BlockState getBlockState(BlockPos pos) {
                    return level.getBlockState(pos);
                }
                
                @Override
                public FluidState getFluidState(BlockPos pos) {
                    return level.getFluidState(pos);
                }
                
                @Override
                public int getHeight() {
                    return level.getHeight();
                }
                
                @Override
                public int getMinY() {
                    return level.getMinY();
                }
            } : level;
            var color = -1;
            for (var source : tintSource) {
                var tintColor = source.colorInWorld(state, blockAndTintGetter, pos);
                if (tintColor == -1) {
                    tintColor = source.colorAsTerrainParticle(state, blockAndTintGetter, pos);
                }
                color = VanillaUtils.mulColor(color, tintColor);
            }
            return color;
        }
    }
}
