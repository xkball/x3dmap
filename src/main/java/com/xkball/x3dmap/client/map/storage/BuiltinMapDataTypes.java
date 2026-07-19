package com.xkball.x3dmap.client.map.storage;

import com.xkball.x3dmap.X3dMap;
import com.xkball.x3dmap.api.client.storage.LevelDataType;
import com.xkball.x3dmap.client.map.uistate.WorldMapUiStateStorage;
import com.xkball.x3dmap.client.map.waypoint.WaypointStorage;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

@NonNullByDefault
public final class BuiltinMapDataTypes {

    public static final LevelDataType<WorldMapUiStateStorage> UI_STATE = LevelDataType.create(
            Identifier.fromNamespaceAndPath(X3dMap.MODID, "ui_state"),
            WorldMapUiStateStorage.STREAM_CODEC,
            WorldMapUiStateStorage::new
    );
    public static final LevelDataType<WaypointStorage> WAYPOINTS = LevelDataType.create(
            Identifier.fromNamespaceAndPath(X3dMap.MODID, "waypoint"),
            WaypointStorage.STREAM_CODEC,
            WaypointStorage::new
    );

    private BuiltinMapDataTypes() {
    }
}
