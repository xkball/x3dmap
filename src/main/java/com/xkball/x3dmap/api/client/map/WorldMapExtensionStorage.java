package com.xkball.x3dmap.api.client.map;

import io.netty.buffer.ByteBuf;

public interface WorldMapExtensionStorage {
    
    String extensionId();
    
    boolean dirty();
    
    void clearDirty();
    
    void load(ByteBuf byteBuf);
    
    void save(ByteBuf byteBuf);
}
