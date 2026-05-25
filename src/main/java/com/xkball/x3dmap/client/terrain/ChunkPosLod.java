package com.xkball.x3dmap.client.terrain;

import net.minecraft.world.level.ChunkPos;

public record ChunkPosLod(ChunkPos chunkPos, int lodLevel) {
}
