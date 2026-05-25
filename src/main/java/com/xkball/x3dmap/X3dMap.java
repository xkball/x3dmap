package com.xkball.x3dmap;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.server.ChunkBatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.function.Consumer;

@Mod(X3dMap.MODID)
@EventBusSubscriber(modid = X3dMap.MODID)
public class X3dMap {

    public static final String MODID = "x3d_map";

    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<TicketType> TICKET_TYPE = DeferredRegister.create(BuiltInRegistries.TICKET_TYPE, X3dMap.MODID);
    public static final DeferredHolder<TicketType, TicketType> MAP_GEOMATICS = TICKET_TYPE.register("map_geomatics", () -> new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING));
    public static Consumer<LevelChunk> MARK_DIRTY_CALLBACK = (c) -> {};

    public X3dMap(IEventBus modEventBus, ModContainer modContainer) {
        TICKET_TYPE.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, ServerConfig.SPEC);
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ChunkBatcher.init(event.getServer());
    }
    
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ChunkBatcher.deInit();
    }

    @SubscribeEvent
    public static void onEndServerTick(ServerTickEvent.Post event) {
        ChunkBatcher.scheduleIfNeeded(event.getServer());
    }
}
