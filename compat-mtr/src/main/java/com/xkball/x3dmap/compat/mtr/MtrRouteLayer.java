package com.xkball.x3dmap.compat.mtr;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.api.client.render.IMap3dLayer;
import com.xkball.x3dmap.api.client.render.IMap3dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMap3dRenderContext;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.x3dmap.api.client.render.IMapLayerContext;
import com.xkball.x3dmap.client.render.pip.layers.GridRenderer;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.client.b3d.uniform.XKLibUniforms;
import mtr.client.ClientData;
import mtr.data.Platform;
import mtr.data.Rail;
import mtr.data.SavedRailBase;
import mtr.path.PathData;
import mtr.path.PathFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@NonNullByDefault
public final class MtrRouteLayer implements IMap3dLayer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final IMapLayerContext layerContext;
    private volatile long cachedDataHash = Long.MIN_VALUE;
    private volatile long pendingDataHash = Long.MIN_VALUE;
    private volatile List<RouteSegment> cachedSegments = List.of();

    public MtrRouteLayer(IMapLayerContext layerContext) {
        this.layerContext = layerContext;
    }
    
    @Override
    public @Nullable IMap3dRenderCommand prepareRender(IMapFrame frame) {
        var routePlans = createRoutePlans();
        var dataHash = calculateDataHash(routePlans);
        if (dataHash != this.cachedDataHash && dataHash != this.pendingDataHash) {
            this.pendingDataHash = dataHash;
            var platforms = copyPlatforms();
            var rails = copyRails(platforms);
            CompletableFuture.supplyAsync(() -> createSegments(routePlans, platforms, rails))
                    .whenComplete((segments, throwable) -> this.finishPathGeneration(dataHash, segments, throwable));
        }
        var segments = this.cachedSegments;
        if (segments.isEmpty()) {
            return null;
        }
        return context -> render(context, segments);
    }

    private void finishPathGeneration(long dataHash, @Nullable List<RouteSegment> segments, @Nullable Throwable throwable) {
        if (this.pendingDataHash != dataHash) {
            return;
        }
        if (throwable != null || segments == null) {
            this.pendingDataHash = Long.MIN_VALUE;
            LOGGER.error("Failed to generate MTR route paths", throwable);
            return;
        }
        this.cachedSegments = segments;
        this.cachedDataHash = dataHash;
        this.pendingDataHash = Long.MIN_VALUE;
        this.layerContext.invalidate();
    }

    private static List<RoutePlan> createRoutePlans() {
        var routePlans = new ArrayList<RoutePlan>();
        for (var route : List.copyOf(ClientData.ROUTES)) {
            if (route.isHidden) {
                continue;
            }
            var platformIds = new ArrayList<Long>();
            for (var routePlatform : List.copyOf(route.platformIds)) {
                platformIds.add(routePlatform.platformId);
            }
            routePlans.add(new RoutePlan(0xFF000000 | route.color, List.copyOf(platformIds)));
        }
        return List.copyOf(routePlans);
    }

    private static long calculateDataHash(List<RoutePlan> routePlans) {
        var hash = 31L * ClientData.RAILS.hashCode() + routePlans.hashCode();
        for (var platform : ClientData.PLATFORMS) {
            hash = 31 * hash + Long.hashCode(platform.id);
            hash = 31 * hash + System.identityHashCode(platform);
        }
        return hash;
    }

    private static Map<Long, Platform> copyPlatforms() {
        var platforms = new HashMap<Long, Platform>();
        for (var platform : List.copyOf(ClientData.PLATFORMS)) {
            platforms.put(platform.id, platform);
        }
        return platforms;
    }

    private static Map<BlockPos, Map<BlockPos, Rail>> copyRails(Map<Long, Platform> platforms) {
        var rails = new HashMap<BlockPos, Map<BlockPos, Rail>>();
        for (var entry : ClientData.RAILS.entrySet()) {
            rails.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        for (var connections : List.copyOf(rails.values())) {
            for (var connectedPos : connections.keySet()) {
                rails.computeIfAbsent(connectedPos, _ -> new HashMap<>());
            }
        }
        for (var platform : platforms.values()) {
            for (var platformPos : platform.getOrderedPositions(BlockPos.ZERO, false)) {
                rails.computeIfAbsent(platformPos, _ -> new HashMap<>());
            }
        }
        return rails;
    }

    private static List<RouteSegment> createSegments(
            List<RoutePlan> routePlans,
            Map<Long, Platform> platforms,
            Map<BlockPos, Map<BlockPos, Rail>> rails
    ) {
        var segments = new ArrayList<RouteSegment>();
        for (var routePlan : routePlans) {
            for (var index = 0; index < routePlan.platformIds().size() - 1; index++) {
                var startPlatform = platforms.get(routePlan.platformIds().get(index));
                var endPlatform = platforms.get(routePlan.platformIds().get(index + 1));
                if (startPlatform == null || endPlatform == null) {
                    continue;
                }
                var platformPair = new ArrayList<SavedRailBase>(2);
                platformPair.add(startPlatform);
                platformPair.add(endPlatform);
                var path = new ArrayList<PathData>();
                try {
                    PathFinder.findPath(path, rails, platformPair, 1, 0, false);
                } catch (RuntimeException e) {
                    LOGGER.error(
                            "Failed to find MTR route path between platforms {} and {}",
                            startPlatform.id,
                            endPlatform.id,
                            e
                    );
                    continue;
                }
                for (var pathData : path) {
                    addRailSegments(segments, pathData.rail, routePlan.color());
                }
            }
        }
        return List.copyOf(segments);
    }

    private static void addRailSegments(List<RouteSegment> segments, Rail rail, int color) {
        var length = rail.getLength();
        if (!Double.isFinite(length) || length <= 0) {
            return;
        }
        var sampleCount = Math.max(1, (int) Math.ceil(length));
        var previous = rail.getPosition(0);
        for (var index = 1; index <= sampleCount; index++) {
            var current = rail.getPosition(length * index / sampleCount);
            if (previous.distanceToSqr(current) > 0.000001) {
                segments.add(new RouteSegment(previous, current, color));
            }
            previous = current;
        }
    }
    
    private static void render(IMap3dRenderContext context, List<RouteSegment> segments) {
        var texture = context.colorTarget();
        XKLibUniforms.SCREEN_SIZE.startOverride(builder -> builder.putVec2(texture.getWidth(0), texture.getHeight(0)));
        try {
            var buffer = context.bufferSource().getBuffer(MtrRenderPipelines.ROUTE_LINE.asRenderType());
            var pose = context.poseStack().last();
            for (var segment : segments) {
                GridRenderer.tryDrawLine3D(
                        buffer,
                        pose,
                        (float) segment.start().x,
                        (float) segment.start().y + 1.0f,
                        (float) segment.start().z,
                        (float) segment.end().x,
                        (float) segment.end().y + 1.0f,
                        (float) segment.end().z,
                        segment.color(),
                        segment.color()
                );
            }
            context.bufferSource().endLastBatch();
        } finally {
            XKLibUniforms.SCREEN_SIZE.endOverride();
        }
    }

    private record RoutePlan(int color, List<Long> platformIds) {
    }
    
    private record RouteSegment(Vec3 start, Vec3 end, int color) {
    }
}
