package com.xkball.x3dmap;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ServerConfig {
    
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ALLOW_SERVER_SENT_CHUNK;
    
    static {
        var builder = new ModConfigSpec.Builder();
        ALLOW_SERVER_SENT_CHUNK = builder
                .comment("Allow server-side sent chunk to client. When disabled, clients cannot request server-side chunk re-rendering.")
                .define("allowServerSentChunk", false);
        SPEC = builder.build();
    }
}
