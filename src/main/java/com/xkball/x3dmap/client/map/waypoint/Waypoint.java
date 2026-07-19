package com.xkball.x3dmap.client.map.waypoint;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

@NonNullByDefault
public class Waypoint {

    public static final StreamCodec<ByteBuf, Waypoint> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            Waypoint::id,
            ByteBufCodecs.STRING_UTF8,
            Waypoint::name,
            BlockPos.STREAM_CODEC,
            Waypoint::pos,
            ByteBufCodecs.INT,
            Waypoint::color,
            ByteBufCodecs.BOOL,
            Waypoint::hidden,
            Waypoint::new
    );
    
    private final UUID id;
    private String name;
    private BlockPos pos;
    private int color;
    private boolean hidden;
    
    public Waypoint(UUID id, String name, BlockPos pos, int color, boolean hidden) {
        this.id = id;
        this.name = name;
        this.pos = pos;
        this.color = color;
        this.hidden = hidden;
    }
    
    public UUID id() {
        return this.id;
    }
    
    public String name() {
        return this.name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public BlockPos pos() {
        return this.pos;
    }
    
    public void setPos(BlockPos pos) {
        this.pos = pos;
    }
    
    public int color() {
        return this.color;
    }
    
    public void setColor(int color) {
        this.color = color;
    }
    
    public boolean hidden() {
        return this.hidden;
    }
    
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}
