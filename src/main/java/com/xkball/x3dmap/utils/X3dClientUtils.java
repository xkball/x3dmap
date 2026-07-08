package com.xkball.x3dmap.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;

public class X3dClientUtils {
    
    public static boolean isClientChunkLoaded(int chunkX, int chunkZ) {
        var level = Minecraft.getInstance().level;
        if (level == null) return false;
        var chunkSource = level.getChunkSource();
        var storage = chunkSource.storage;
        return storage.inRange(chunkX, chunkZ);
    }
    
    public static boolean isClientChunkAroundLoaded(int chunkX, int chunkZ){
        var level = Minecraft.getInstance().level;
        if (level == null) return false;
        var chunkSource = level.getChunkSource();
        var storage = chunkSource.storage;
        return  storage.inRange(chunkX,chunkZ) &&
                storage.inRange(chunkX-1,chunkZ) &&
                storage.inRange(chunkX+1,chunkZ) &&
                storage.inRange(chunkX,chunkZ-1) &&
                storage.inRange(chunkX,chunkZ+1);
    }
    
    public static boolean isClientChunkAroundLoaded(ChunkPos chunkPos){
        var x = chunkPos.x();
        var z = chunkPos.z();
        return isClientChunkAroundLoaded(x, z);
    }
}
