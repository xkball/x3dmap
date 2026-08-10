package com.xkball.x3dmap.api.client.runtime;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.world.level.ChunkPos;

import java.util.OptionalInt;

@NonNullByDefault
public interface ITerrainView {

    OptionalInt height(int x, int z);

    int color(int x, int z);

    boolean containsChunk(ChunkPos chunkPos);
}
