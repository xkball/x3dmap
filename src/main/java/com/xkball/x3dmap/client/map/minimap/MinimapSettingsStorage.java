package com.xkball.x3dmap.client.map.minimap;

import com.xkball.x3dmap.api.client.map.WorldMapExtensionStorage;
import io.netty.buffer.ByteBuf;

public class MinimapSettingsStorage implements WorldMapExtensionStorage {
    
    public static final String EXTENSION_ID = "minimap_settings";
    private static final int VERSION = 1;
    
    int highDetailRange = 8;
    boolean rotateWithPlayer = false;
    float camXRot = 89.0f;
    float camFov = 60.0f;
    float camCameraLength = 0.0f;
    private boolean dirty;
    
    @Override
    public String extensionId() {
        return EXTENSION_ID;
    }
    
    @Override
    public boolean dirty() {
        return dirty;
    }
    
    @Override
    public void clearDirty() {
        dirty = false;
    }
    
    public void markDirty() {
        dirty = true;
    }
    
    @Override
    public void load(ByteBuf buf) {
        var version = buf.readInt();
        if (version != VERSION) {
            return;
        }
        highDetailRange = buf.readInt();
        rotateWithPlayer = buf.readBoolean();
        camXRot = buf.readFloat();
        camFov = buf.readFloat();
        camCameraLength = buf.readFloat();
        clearDirty();
        if (MinimapExtension.INSTANCE != null) {
            MinimapExtension.INSTANCE.highDetailRange.set(this.highDetailRange);
            MinimapExtension.INSTANCE.rotateWithPlayer.set(this.rotateWithPlayer);
        }
    }
    
    @Override
    public void save(ByteBuf buf) {
        buf.writeInt(VERSION);
        buf.writeInt(highDetailRange);
        buf.writeBoolean(rotateWithPlayer);
        buf.writeFloat(camXRot);
        buf.writeFloat(camFov);
        buf.writeFloat(camCameraLength);
    }
}
