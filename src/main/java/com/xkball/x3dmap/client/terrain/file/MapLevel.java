package com.xkball.x3dmap.client.terrain.file;

import com.xkball.x3dmap.client.terrain.RegionPos;
import com.xkball.x3dmap.utils.ExpiringResourceCache;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import java.nio.file.Path;

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
}
