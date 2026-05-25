package com.xkball.x3dmap;

import com.xkball.x3dmap.client.map.minimap.MinimapExtension;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class ClientConfig {
    
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue FORCE_COMPATIBILITY_MODE;
    public static final ModConfigSpec.BooleanValue MINIMAP_ENABLED;
    public static final ModConfigSpec.IntValue MINIMAP_SIZE;
    public static final ModConfigSpec.IntValue MINIMAP_PADDING;
    public static final ModConfigSpec.IntValue AUTO_SAVE_INTERVAL;
    public static final ModConfigSpec.IntValue DRAW_NEW_CHUNK_INTERVAL;
    public static final ModConfigSpec.IntValue DRAW_NEW_CHUNK_COUNT;
    public static final ModConfigSpec.BooleanValue SHOW_MAP_INFO;
    
    static {
        var builder = new ModConfigSpec.Builder();
        FORCE_COMPATIBILITY_MODE = builder
                .comment("Force compatibility mode. When enabled, disables MDI, sparse texture and SSBO rendering features even if the GPU supports them.")
                .define("forceCompatibilityMode", false);
        MINIMAP_ENABLED = builder
                .comment("Enable the minimap HUD overlay.")
                .define("minimapEnabled", true);
        MINIMAP_SIZE = builder
                .comment("Minimap size as percentage of screen height. Default: 15.")
                .defineInRange("minimapSize", 25, 1, 50);
        MINIMAP_PADDING = builder
                .comment("Minimap padding from screen edge as percentage of screen height. Default: 5.")
                .defineInRange("minimapPadding", 5, 0, 25);
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
        if (MinimapExtension.INSTANCE != null) {
            MinimapExtension.INSTANCE.minimapEnabled.set(MINIMAP_ENABLED.get());
        }
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
