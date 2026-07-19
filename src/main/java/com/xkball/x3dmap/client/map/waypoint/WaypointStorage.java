package com.xkball.x3dmap.client.map.waypoint;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@NonNullByDefault
public class WaypointStorage {
    
    public static final StreamCodec<ByteBuf, WaypointStorage> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public WaypointStorage decode(ByteBuf input) {
            var storage = new WaypointStorage();
            storage.load(input);
            return storage;
        }

        @Override
        public void encode(ByteBuf output, WaypointStorage value) {
            value.save(output);
        }
    };
    private static final int VERSION = 1;
    private final List<Waypoint> waypoints = new ArrayList<>();
    private final List<Runnable> dirtyListeners = new ArrayList<>();

    public void addDirtyListener(Runnable dirtyListener) {
        this.dirtyListeners.add(dirtyListener);
    }

    public void removeDirtyListener(Runnable dirtyListener) {
        this.dirtyListeners.remove(dirtyListener);
    }
    
    public void markDirty() {
        for (var listener : List.copyOf(this.dirtyListeners)) {
            listener.run();
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
    }
    
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
