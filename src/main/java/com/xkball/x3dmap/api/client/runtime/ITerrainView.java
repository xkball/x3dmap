package com.xkball.x3dmap.api.client.runtime;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.OptionalInt;

@NonNullByDefault
public interface ITerrainView {

    OptionalInt height(ResourceKey<Level> dimension, int x, int z);

    int color(ResourceKey<Level> dimension, int x, int z);

    boolean containsChunk(ResourceKey<Level> dimension, ChunkPos chunkPos);
}
