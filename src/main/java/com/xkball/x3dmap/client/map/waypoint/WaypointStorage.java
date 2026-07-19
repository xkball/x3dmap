package com.xkball.x3dmap.client.map.waypoint;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@NonNullByDefault
public class WaypointStorage {
    
    private static final StreamCodec<ByteBuf, List<Waypoint>> WAYPOINT_LIST_CODEC = ByteBufCodecs.collection(ArrayList::new, Waypoint.STREAM_CODEC);
    public static final StreamCodec<ByteBuf, WaypointStorage> STREAM_CODEC = StreamCodec.composite(
            WAYPOINT_LIST_CODEC,
            storage -> storage.waypoints,
            WaypointStorage::new
    );
    private final List<Waypoint> waypoints = new ArrayList<>();
    private final List<Runnable> dirtyListeners = new ArrayList<>();

    public WaypointStorage() {
    }

    private WaypointStorage(List<Waypoint> waypoints) {
        this.waypoints.addAll(waypoints);
    }

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
}
