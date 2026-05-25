package com.xkball.x3dmap.client.map.waypoint;

import com.xkball.x3dmap.api.client.map.WorldMapExtensionStorage;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class WaypointStorage implements WorldMapExtensionStorage {

    public static final String EXTENSION_ID = "waypoint";
    private static final int VERSION = 1;
    private final List<Waypoint> waypoints = new ArrayList<>();
    private boolean dirty;
    public Runnable onMarkDirty = null;

    @Override
    public String extensionId() {
        return EXTENSION_ID;
    }

    @Override
    public boolean dirty() {
        return this.dirty;
    }

    @Override
    public void clearDirty() {
        this.dirty = false;
    }

    public void markDirty() {
        this.dirty = true;
        if(this.onMarkDirty != null) {
            this.onMarkDirty.run();
        }
    }

    public List<Waypoint> waypoints() {
        return Collections.unmodifiableList(this.waypoints);
    }

    public void add(Waypoint waypoint) {
        this.waypoints.add(waypoint);
        this.markDirty();
    }

    public void remove(Waypoint waypoint) {
        this.waypoints.remove(waypoint);
        this.markDirty();
    }

    @Override
    public void load(ByteBuf byteBuf) {
        this.waypoints.clear();
        var version = byteBuf.readInt();
        if (version != VERSION) {
            return;
        }
        var size = byteBuf.readInt();
        for (var i = 0; i < size; i++) {
            var id = new UUID(byteBuf.readLong(), byteBuf.readLong());
            var name = this.readString(byteBuf);
            var pos = new BlockPos(byteBuf.readInt(), byteBuf.readInt(), byteBuf.readInt());
            var color = byteBuf.readInt();
            var hidden = byteBuf.readBoolean();
            this.waypoints.add(new Waypoint(id, name, pos, color, hidden));
        }
        this.clearDirty();
    }

    @Override
    public void save(ByteBuf byteBuf) {
        byteBuf.writeInt(VERSION);
        byteBuf.writeInt(this.waypoints.size());
        for (var waypoint : this.waypoints) {
            byteBuf.writeLong(waypoint.id().getMostSignificantBits());
            byteBuf.writeLong(waypoint.id().getLeastSignificantBits());
            this.writeString(byteBuf, waypoint.name());
            byteBuf.writeInt(waypoint.pos().getX());
            byteBuf.writeInt(waypoint.pos().getY());
            byteBuf.writeInt(waypoint.pos().getZ());
            byteBuf.writeInt(waypoint.color());
            byteBuf.writeBoolean(waypoint.hidden());
        }
    }

    private void writeString(ByteBuf byteBuf, String value) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        byteBuf.writeInt(bytes.length);
        byteBuf.writeBytes(bytes);
    }

    private String readString(ByteBuf byteBuf) {
        var length = byteBuf.readInt();
        var bytes = new byte[length];
        byteBuf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
