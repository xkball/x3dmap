package com.xkball.x3dmap.client.map.waypoint;

import net.minecraft.core.BlockPos;

import java.util.UUID;

public class Waypoint {
    
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
