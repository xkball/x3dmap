package com.xkball.x3dmap;

import com.xkball.x3dmap.config.DimensionMapConfig;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = X3dMap.MODID)
@NonNullByDefault
public class ServerConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ALLOW_SERVER_SENT_CHUNK;
    public static final ModConfigSpec.ConfigValue<List<? extends List<?>>> DIMENSIONS;
    public static final ModConfigSpec.BooleanValue SHOW_MOTD;
    private static volatile Map<Identifier, DimensionMapConfig> dimensions = Map.of();

    static {
        var builder = new ModConfigSpec.Builder();
        ALLOW_SERVER_SENT_CHUNK = builder
                .comment("Allow server-side sent chunk to client. When disabled, clients cannot request server-side chunk re-rendering.")
                .define("allowServerSentChunk", false);
        DIMENSIONS = builder
                .comment("Per-dimension map settings: [dimension id, map enabled, override sea level, sea level].")
                .defineListAllowEmpty("dimensions", List.<List<?>>of(), DimensionMapConfig::isValidEntry);
        SHOW_MOTD = builder
                .define("showMotd", true);
        SPEC = builder.build();
    }

    public static DimensionMapConfig getDimensionConfig(ResourceKey<Level> dimension, int defaultSeaLevel) {
        var configured = dimensions.get(dimension.identifier());
        return configured == null ? new DimensionMapConfig(dimension.identifier(), true, false, defaultSeaLevel) : configured;
    }

    public static void update() {
        dimensions = DimensionMapConfig.parse(DIMENSIONS.get(), "server config");
    }

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            update();
        }
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            update();
        }
    }
}
