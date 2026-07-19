package com.xkball.x3dmap;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@NonNullByDefault
public class ClientConfig {
    
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue FORCE_COMPATIBILITY_MODE;
    public static final ModConfigSpec.BooleanValue RECORD_ALL_ABOVE_SEA_LEVEL;
    public static final ModConfigSpec.BooleanValue MINIMAP_ENABLED;
    public static final ModConfigSpec.IntValue MINIMAP_HIGH_DETAIL_RANGE;
    public static final ModConfigSpec.BooleanValue MINIMAP_ROTATE_WITH_PLAYER;
    public static final ModConfigSpec.DoubleValue MINIMAP_CAMERA_X_ROT;
    public static final ModConfigSpec.DoubleValue MINIMAP_CAMERA_FOV;
    public static final ModConfigSpec.DoubleValue MINIMAP_CAMERA_LENGTH;
    public static final ModConfigSpec.IntValue MINIMAP_SIZE;
    public static final ModConfigSpec.IntValue MINIMAP_PADDING;
    public static final ModConfigSpec.IntValue MINIMAP_RENDER_INTERVAL;
    public static final ModConfigSpec.IntValue AUTO_SAVE_INTERVAL;
    public static final ModConfigSpec.IntValue DRAW_NEW_CHUNK_INTERVAL;
    public static final ModConfigSpec.IntValue DRAW_NEW_CHUNK_COUNT;
    public static final ModConfigSpec.BooleanValue SHOW_MAP_INFO;
    
    static {
        var builder = new ModConfigSpec.Builder();
        FORCE_COMPATIBILITY_MODE = builder
                .comment("Force compatibility mode. When enabled, disables MDI, sparse texture and SSBO rendering features even if the GPU supports them.")
                .define("forceCompatibilityMode", false);
        RECORD_ALL_ABOVE_SEA_LEVEL = builder
                .comment("Record all blocks above sea level. This uses more VRAM when enabled.")
                .define("recordAllAboveSeaLevel", true);
        MINIMAP_ENABLED = builder
                .comment("Enable the minimap HUD overlay.")
                .define("minimapEnabled", true);
        MINIMAP_HIGH_DETAIL_RANGE = builder
                .comment("Minimap high detail range in chunks.")
                .defineInRange("minimapHighDetailRange", 8, 0, 64);
        MINIMAP_ROTATE_WITH_PLAYER = builder
                .comment("Rotate the minimap with the player.")
                .define("minimapRotateWithPlayer", false);
        MINIMAP_CAMERA_X_ROT = builder
                .comment("Minimap camera X rotation.")
                .defineInRange("minimapCameraXRot", 89.0, -89.9, 89.9);
        MINIMAP_CAMERA_FOV = builder
                .comment("Minimap camera field of view.")
                .defineInRange("minimapCameraFov", 60.0, 5.0, 90.0);
        MINIMAP_CAMERA_LENGTH = builder
                .comment("Minimap camera length.")
                .defineInRange("minimapCameraLength", 0.0, 0.0, 100000.0);
        MINIMAP_SIZE = builder
                .comment("Minimap size as percentage of screen height. Default: 15.")
                .defineInRange("minimapSize", 25, 1, 50);
        MINIMAP_PADDING = builder
                .comment("Minimap padding from screen edge as percentage of screen height. Default: 5.")
                .defineInRange("minimapPadding", 5, 0, 25);
        MINIMAP_RENDER_INTERVAL = builder
                .comment("Interval in frames between minimap re-renders. Higher values improve performance at the cost of update latency. Default: 4.")
                .defineInRange("minimapRenderInterval", 10, 1, 200);
        AUTO_SAVE_INTERVAL = builder
                .comment("Interval in ticks for auto-saving map data. Default: 1200 (60s).")
                .defineInRange("autoSaveInterval", 1200, 20, 72000);
        DRAW_NEW_CHUNK_INTERVAL = builder
                .comment("Interval in ticks for drawing new chunks from the update queue. Default: 20 (1s).")
                .defineInRange("drawNewChunkInterval", 20, 1, 1200);
        DRAW_NEW_CHUNK_COUNT = builder
                .comment("Number of chunks to draw per interval from the update queue. Default: 20.")
                .defineInRange("drawNewChunkCount", 1000, 1, 50000);
        SHOW_MAP_INFO = builder
                .comment("Show map info window when first opening the map. Automatically set to false after shown once.")
                .define("showMapInfo", true);
        SPEC = builder.build();
    }
    
    public static void update() {
    }
    
    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        update();
    }
    
    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        update();
    }
}
