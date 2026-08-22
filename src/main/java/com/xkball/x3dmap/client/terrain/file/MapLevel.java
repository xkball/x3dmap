package com.xkball.x3dmap.client.terrain.file;

import com.xkball.x3dmap.client.terrain.RegionPos;
import com.xkball.x3dmap.client.terrain.render.MapNodeModel;
import com.xkball.x3dmap.utils.ExpiringResourceCache;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@NonNullByDefault
public class MapLevel {
    
    private final Identifier level;
    private final Path dir;
    private final int maxY;
    private final int minY;
    
    private final ExpiringResourceCache<RegionPos, MapRegion> regionCache = ExpiringResourceCache.<RegionPos, MapRegion>builder()
            .loader((pos) -> {
                var result = new MapRegion(getLevel(), pos, getDir());
                result.load();
                return result;
            })
            .expireAfterRead(300)
            .build();
    
    private final ExpiringResourceCache<BlockPos, MapNodeModel> lod0Node = ExpiringResourceCache.<BlockPos, MapNodeModel>builder()
            .asyncLoader(this::createLod0Node)
            .expireAfterRead(20)
            .build();
    
    private final ExpiringResourceCache<BlockPos, MapNodeModel> lod1Node = ExpiringResourceCache.<BlockPos, MapNodeModel>builder()
            .asyncLoader((pos) -> this.createLodNode(pos, 64, lod0Node))
            .expireAfterRead(20)
            .build();
    private final ExpiringResourceCache<BlockPos, MapNodeModel> lod2Node = ExpiringResourceCache.<BlockPos, MapNodeModel>builder()
            .asyncLoader((pos) -> this.createLodNode(pos, 128, lod1Node))
            .expireAfterRead(20)
            .build();;
    private final ExpiringResourceCache<BlockPos, MapNodeModel> lod3Node = ExpiringResourceCache.<BlockPos, MapNodeModel>builder()
            .asyncLoader((pos) -> this.createLodNode(pos, 256, lod2Node))
            .expireAfterRead(20)
            .build();;
    private final ExpiringResourceCache<BlockPos, MapNodeModel> lod4Node = ExpiringResourceCache.<BlockPos, MapNodeModel>builder()
            .asyncLoader((pos) -> this.createLodNode(pos, 512, lod3Node))
            .expireAfterRead(20)
            .build();;
    
    public MapLevel(Level level, Path dir) {
        this.level = level.dimension().identifier();
        this.dir = dir;
        this.maxY = level.getMaxY();
        this.minY = level.getMinY();
    }
    
    public Identifier getLevel() {
        return level;
    }
    
    public Path getDir() {
        return dir;
    }
    
    public void updateChunk(MapChunk chunk){
        var pos = chunk.chunkPos;
        this.regionCache.getAsync(RegionPos.ofChunk(pos))
                .thenAccept(region -> region.setChunk(chunk))
                .thenRun(() -> this.invalidateLODs(pos));
    }
    
    private void invalidateLODs(ChunkPos pos){
        this.invalidateLOD(pos, 1, this.lod0Node);
        this.invalidateLOD(pos, 2, this.lod1Node);
        this.invalidateLOD(pos, 3, this.lod2Node);
        this.invalidateLOD(pos, 4, this.lod3Node);
        this.invalidateLOD(pos, 5, this.lod4Node);
    }

    private void invalidateLOD(ChunkPos pos, int shift, ExpiringResourceCache<BlockPos, MapNodeModel> cache) {
        var x = pos.x() >> shift;
        var z = pos.z() >> shift;
        var minNodeY = SectionPos.blockToSectionCoord(this.minY) >> shift;
        var maxNodeY = SectionPos.blockToSectionCoord(this.maxY) >> shift;
        for (var y = minNodeY; y <= maxNodeY; y++) {
            cache.remove(new BlockPos(x, y, z));
        }
    }
    
    @SuppressWarnings("SequencedCollectionMethodCanBeUsed")
    private CompletableFuture<MapNodeModel> createLod0Node(BlockPos pos) {
        var chunkPos = ChunkPos.containing(pos);
        var regionPos = RegionPos.ofChunk(chunkPos);
        return this.regionCache.getAsync(regionPos)
                .thenCompose((r) -> r.getMapChunkViews(List.of(
                        chunkPos,
                        new ChunkPos(chunkPos.x()+1, chunkPos.z()),
                        new ChunkPos(chunkPos.x(), chunkPos.z()+1),
                        new ChunkPos(chunkPos.x()+1, chunkPos.z()+1))))
                .thenApply((list) -> new MapNodeModel(chunkPos, SectionPos.blockToSectionCoord(pos.getY()),list.get(0),list.get(1), list.get(2), list.get(3)));
    }
    
    private CompletableFuture<MapNodeModel> createLodNode(BlockPos pos, int sideLength, ExpiringResourceCache<BlockPos, MapNodeModel> subNodeSource){
        var px = Math.floorDiv(pos.getX(), sideLength);
        var py = Math.floorDiv(pos.getY(), sideLength);
        var pz = Math.floorDiv(pos.getZ(), sideLength);
        var subSideLength = sideLength / 2;
        return subNodeSource.getListAsync(List.of(
                        new BlockPos(px,py,pz),
                        new BlockPos(px + subSideLength,py,pz),
                        new BlockPos(px,py,pz + subSideLength),
                        new BlockPos(px + subSideLength,py,pz + subSideLength),
                        new BlockPos(px,py + subSideLength,pz),
                        new BlockPos(px + subSideLength,py + subSideLength,pz),
                        new BlockPos(px,py + subSideLength,pz + subSideLength),
                        new BlockPos(px + subSideLength,py + subSideLength,pz + subSideLength)
                ))
                .thenApply(MapNodeModel::new);
    }
}
