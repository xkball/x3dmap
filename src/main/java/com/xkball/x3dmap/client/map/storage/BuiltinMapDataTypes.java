package com.xkball.x3dmap.client.map.storage;

import com.xkball.x3dmap.api.client.storage.LevelDataType;
import com.xkball.x3dmap.client.map.uistate.WorldMapUiStateStorage;
import com.xkball.x3dmap.client.map.waypoint.WaypointStorage;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public final class BuiltinMapDataTypes {

    public static final LevelDataType<WorldMapUiStateStorage> UI_STATE = LevelDataType.create(
            VanillaUtils.modRL("ui_state"),
            WorldMapUiStateStorage.STREAM_CODEC,
            WorldMapUiStateStorage::new
    );
    public static final LevelDataType<WaypointStorage> WAYPOINTS = LevelDataType.create(
            VanillaUtils.modRL("waypoint"),
            WaypointStorage.STREAM_CODEC,
            WaypointStorage::new
    );

    private BuiltinMapDataTypes() {
    }
}
