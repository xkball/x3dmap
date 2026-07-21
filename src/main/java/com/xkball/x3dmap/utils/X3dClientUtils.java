package com.xkball.x3dmap.utils;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.util.FileUtil;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@NonNullByDefault
public class X3dClientUtils {

    public static String getEncodedSaveOrServerName() {
        var player = Minecraft.getInstance().player;
        if(player == null) return "unknown";
        var server = ServerLifecycleHooks.getCurrentServer();
        if(server == null) {
            var serverData = player.connection.getServerData();
            var dataName = serverData == null ? "unkonwn" : serverData.name;
            return FileUtil.sanitizeName(dataName);
        }
        var path = server.getWorldPath(LevelResource.ROOT).getParent().getFileName().toString();
        return FileUtil.sanitizeName(path);
    }
    
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
