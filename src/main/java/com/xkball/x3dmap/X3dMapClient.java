package com.xkball.x3dmap;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.client.map.storage.BuiltinMapDataTypes;
import com.xkball.x3dmap.client.map.minimap.MinimapHudRenderer;
import com.xkball.x3dmap.client.map.waypoint.Waypoint;
import com.xkball.x3dmap.client.render.pip.WorldTerrainPipRenderer;
import com.xkball.x3dmap.client.terrain.ChunkComplier;
import com.xkball.x3dmap.client.terrain.TerrainChunkManager;
import com.xkball.x3dmap.ui.WorldTerrainScreen;
import com.xkball.x3dmap.utils.BarrierExecutor;
import com.xkball.x3dmap.utils.MonitoredExecutor;
import com.xkball.x3dmap.utils.TimeBudgetExecutor;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklib.XKLib;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppedEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Mod(value = X3dMap.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = X3dMap.MODID, value = Dist.CLIENT)
@NonNullByDefault
public class X3dMapClient {
    
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Lazy<KeyMapping> OPEN_MAP_KEY = Lazy.of(() -> new KeyMapping(
            "keys.xklibmc.open_map",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            KeyMapping.Category.MISC
    ));
    
    private static final BarrierExecutor taskExecutorInner = VanillaUtils.fixedSizeExecutor("x3dmap_task_",6);
    public static final Executor taskExecutor = XKLib.IS_DEBUG ? new MonitoredExecutor(taskExecutorInner) : taskExecutorInner;
    public static final Executor ioExecutor = XKLib.IS_DEBUG ? new MonitoredExecutor(VanillaUtils.fixedSizeExecutor("x3dmap_io_",4)) : VanillaUtils.fixedSizeExecutor("x3dmap_io_",4);
    private static final TimeBudgetExecutor mainThreadExecutorInner = new TimeBudgetExecutor();
    public static final Executor mainThreadExecutor = XKLib.IS_DEBUG ? new MonitoredExecutor(mainThreadExecutorInner) : mainThreadExecutorInner;
    
    //todo 很不优雅
    public static boolean loading = false;
    

    public X3dMapClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        X3dMap.MARK_DIRTY_CALLBACK = c -> {
            if (!c.getLevel().isClientSide()) {
                return;
            }
            TerrainChunkManager.INSTANCE.enqueueUpdate(c.getPos());
        };
    }
    
    public static void submitTaskExecutorBarrier(){
        X3dMapClient.taskExecutorInner.submitBarrier();
    }
    
    private static void closeExecutor(Executor executor) {
        if (!(executor instanceof AutoCloseable closeable)) return;
        try {
            closeable.close();
        } catch (Exception e) {
            LOGGER.error("Failed to close terrain executor", e);
        }
    }
    
    @SubscribeEvent
    public static void onGameExit(ClientStoppedEvent event) {
        closeExecutor(X3dMapClient.taskExecutor);
        closeExecutor(X3dMapClient.mainThreadExecutor);
        closeExecutor(X3dMapClient.ioExecutor);
    }
    
    
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        TerrainChunkManager.INSTANCE.initializeMapApi();
        ChunkComplier.init();
    }

    @SubscribeEvent
    public static void registerBindings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MAP_KEY.get());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        X3dMapClient.mainThreadExecutorInner.runFor(5, TimeUnit.MILLISECONDS);
        while (OPEN_MAP_KEY.get().consumeClick()) {
            var mc = Minecraft.getInstance();
            if (mc.screen == null && mc.level != null) {
                mc.setScreen(new WorldTerrainScreen());
            }
        }
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("x3dmap")
                        .then(Commands.literal("waypoint")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("x", IntegerArgumentType.integer(-30000000, 30000000))
                                                .then(Commands.argument("y", IntegerArgumentType.integer(-2048, 2048))
                                                        .then(Commands.argument("z", IntegerArgumentType.integer(-30000000, 30000000))
                                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                                        .executes(context -> addWaypoint(
                                                                                context.getSource(),
                                                                                IntegerArgumentType.getInteger(context, "x"),
                                                                                IntegerArgumentType.getInteger(context, "y"),
                                                                                IntegerArgumentType.getInteger(context, "z"),
                                                                                StringArgumentType.getString(context, "name")
                                                                        ))))))))
        );
    }

    private static int addWaypoint(CommandSourceStack source, int x, int y, int z, String name) {
        try {
            TerrainChunkManager.INSTANCE.initializeMapApi();
            var access = TerrainChunkManager.INSTANCE.mapPluginRegistry.runtime().storage().currentLevelData();
            if (access.isEmpty()) {
                source.sendFailure(Component.translatable("xklibmc.waypoint.add.failure"));
                return 0;
            }
            access.get().get(BuiltinMapDataTypes.WAYPOINTS).value()
                    .add(new Waypoint(UUID.randomUUID(), name, new BlockPos(x, y, z), 0xFF66CCFF, false));
            source.sendSuccess(() -> Component.translatable("xklibmc.waypoint.add.success", name), false);
            return 1;
        } catch (RuntimeException exception) {
            X3dMap.LOGGER.error("Failed to add waypoint", exception);
            source.sendFailure(Component.translatable("xklibmc.waypoint.add.failure"));
            return 0;
        }
    }

    @SubscribeEvent
    public static void onRegPIP(RegisterPictureInPictureRenderersEvent event) {
        event.register(WorldTerrainPipRenderer.WorldTerrainState.class, WorldTerrainPipRenderer::new);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, VanillaUtils.modRL("minimap"), MinimapHudRenderer::render);
    }

}
