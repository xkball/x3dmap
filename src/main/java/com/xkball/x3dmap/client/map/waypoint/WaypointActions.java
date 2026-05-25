package com.xkball.x3dmap.client.map.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.server.permissions.Permissions;

public class WaypointActions {
    
    public static boolean canTeleport() {
        var player = Minecraft.getInstance().player;
        return player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
    
    public static void teleport(Waypoint waypoint) {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return;
        }
        player.connection.sendCommand("tp " + waypoint.pos().getX() + " " + (waypoint.pos().getY() + 1) + " " + waypoint.pos().getZ());
    }
    
    public static void share(Waypoint waypoint) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        player.connection.sendChat(waypoint.name() + " " + waypoint.pos().toShortString());
    }
}
