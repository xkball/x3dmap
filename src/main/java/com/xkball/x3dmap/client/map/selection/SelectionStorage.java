package com.xkball.x3dmap.client.map.selection;

import net.minecraft.world.level.ChunkPos;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SelectionStorage {

    private final Set<ChunkPos> selectedChunks = new HashSet<>();

    public Set<ChunkPos> selectedChunks() {
        return Collections.unmodifiableSet(this.selectedChunks);
    }

    public void add(ChunkPos pos) {
        this.selectedChunks.add(pos);
    }

    public void addAll(Set<ChunkPos> positions) {
        this.selectedChunks.addAll(positions);
    }

    public void remove(ChunkPos pos) {
        this.selectedChunks.remove(pos);
    }

    public void clear() {
        this.selectedChunks.clear();
    }

    public boolean isEmpty() {
        return this.selectedChunks.isEmpty();
    }
}
