package com.xkball.x3dmap;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.neoforged.neoforge.common.ModConfigSpec;

@NonNullByDefault
public class ServerConfig {
    
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ALLOW_SERVER_SENT_CHUNK;
    public static final ModConfigSpec.BooleanValue OVERRIDE_SEA_LEVEL;
    public static final ModConfigSpec.IntValue SEA_LEVEL_OVERRIDE;
    
    static {
        var builder = new ModConfigSpec.Builder();
        ALLOW_SERVER_SENT_CHUNK = builder
                .comment("Allow server-side sent chunk to client. When disabled, clients cannot request server-side chunk re-rendering.")
                .define("allowServerSentChunk", false);
        OVERRIDE_SEA_LEVEL = builder
                .comment("Override the sea level height used when compiling chunks.")
                .define("overrideSeaLevel", false);
        SEA_LEVEL_OVERRIDE = builder
                .comment("Sea level height used when sea level override is enabled.")
                .defineInRange("seaLevelOverride", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        SPEC = builder.build();
    }
    
    public static int getSeaLevel(int defaultSeaLevel) {
        if (OVERRIDE_SEA_LEVEL.get()) {
            return SEA_LEVEL_OVERRIDE.get();
        }
        return defaultSeaLevel;
    }
}
