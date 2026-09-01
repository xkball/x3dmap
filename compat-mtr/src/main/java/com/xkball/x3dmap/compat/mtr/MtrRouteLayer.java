package com.xkball.x3dmap.compat.mtr;

import com.lx862.tprobe3.data.DepotPathData;
import com.lx862.tprobe3.packet.PacketTProbeRequester;
import com.lx862.tprobe3.packet.ResponseCache;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xkball.x3dmap.api.client.render.IMap3dLayer;
import com.xkball.x3dmap.api.client.render.IMap3dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMap3dRenderContext;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.x3dmap.api.client.render.IMapLayerContext;
import com.xkball.x3dmap.client.b3d.pipeline.X3dMapRenderPipelines;
import com.xkball.x3dmap.client.render.pip.layers.GridRenderer;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import com.xkball.xklibmc.client.b3d.uniform.XKLibUniforms;
import mtr.client.ClientData;
import mtr.data.Depot;
import mtr.data.RailType;
import mtr.data.Route;
import mtr.data.Station;
import mtr.data.TransportMode;
import mtr.path.PathData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@NonNullByDefault
public final class MtrRouteLayer implements IMap3dLayer {

    private static final double SAMPLE_DISTANCE = 2.0;
    private static final RenderType ROUTE_RENDER_TYPE = RenderType.create(
            "x3dmap_mtr_routes",
            RenderSetup.builder(X3dMapRenderPipelines.LINE_NO_DEPTH).createRenderSetup()
    );
    private final IMapLayerContext layerContext;
    private final Map<Long, DepotPathData> depotPaths = new HashMap<>();
    private final Set<Long> requestedDepots = new HashSet<>();

    public MtrRouteLayer(IMapLayerContext layerContext) {
        this.layerContext = layerContext;
    }

    @Override
    public @Nullable IMap3dRenderCommand prepareRender(IMapFrame frame) {
        if (!MtrCompatPlugin.transitVisible(this.layerContext, frame)) {
            return null;
        }
        this.requestDepotPaths();
        return this::render;
    }

    private void requestDepotPaths() {
        for (var depot : ClientData.DEPOTS) {
            if (!this.requestedDepots.add(depot.id)) {
                continue;
            }
            PacketTProbeRequester.requestPathData(
                    depot.id,
                    response -> this.receiveDepotPath(depot.id, response),
                    Minecraft.getInstance().getSingleplayerServer()
            );
        }
    }

    private void receiveDepotPath(long depotId, ResponseCache.TProbeDataResponse response) {
        if (!response.packetSuccess() || !(response.data() instanceof DepotPathData depotPathData)) {
            return;
        }
        Minecraft.getInstance().execute(() -> {
            this.depotPaths.put(depotId, depotPathData);
            this.layerContext.invalidate();
        });
    }

    private void render(IMap3dRenderContext context) {
        if (!MtrCompatPlugin.transitVisible(this.layerContext, context.frame()) || this.depotPaths.isEmpty()) {
            return;
        }
        XKLibUniforms.SCREEN_SIZE.startOverride(builder -> builder.putVec2(
                context.colorTarget().getWidth(0),
                context.colorTarget().getHeight(0)
        ));
        this.drawRoutes(context.bufferSource().getBuffer(ROUTE_RENDER_TYPE), context.poseStack().last());
        context.bufferSource().endBatch(ROUTE_RENDER_TYPE);
        XKLibUniforms.SCREEN_SIZE.endOverride();
        
    }

    private void drawRoutes(VertexConsumer buffer, PoseStack.Pose pose) {
        var representativeRoutes = this.findRepresentativeRoutes();
        var renderedRails = new HashSet<RenderedRail>();
        var depots = new ArrayList<>(ClientData.DEPOTS);
        depots.sort(Comparator.comparingLong((Depot depot) -> depot.id));
        for (var depot : depots) {
            var depotPath = this.depotPaths.get(depot.id);
            if (depotPath == null) {
                continue;
            }
            for (var assignedPath : assignRoutes(depot, depotPath)) {
                var routeKey = routeKey(assignedPath.route());
                if (routeKey == null || representativeRoutes.get(routeKey) == null
                        || representativeRoutes.get(routeKey) != assignedPath.route().id) {
                    continue;
                }
                var pathData = assignedPath.pathData();
                if (isTurnBack(pathData)
                        || !renderedRails.add(new RenderedRail(routeKey, pathData.getRailProduct()))) {
                    continue;
                }
                drawRail(pathData, 0xFF000000 | assignedPath.route().color, buffer, pose);
            }
        }
    }

    private Map<RouteKey, Long> findRepresentativeRoutes() {
        var availableRoutes = new HashSet<Long>();
        for (var depotId : this.depotPaths.keySet()) {
            Depot depot = ClientData.DATA_CACHE.depotIdMap.get(depotId);
            if (depot != null) {
                availableRoutes.addAll(depot.routeIds);
            }
        }
        var routes = new ArrayList<>(ClientData.ROUTES);
        routes.sort(Comparator.comparingLong((Route route) -> route.id));
        var representativeRoutes = new HashMap<RouteKey, Long>();
        for (var route : routes) {
            if (route.isHidden || !availableRoutes.contains(route.id)) {
                continue;
            }
            var routeKey = routeKey(route);
            if (routeKey != null) {
                representativeRoutes.putIfAbsent(routeKey, route.id);
            }
        }
        return representativeRoutes;
    }

    private static List<AssignedPath> assignRoutes(Depot depot, DepotPathData depotPath) {
        if (depotPath.mainPath().isEmpty()) {
            return List.of();
        }
        var routePlatforms = new ArrayList<RoutePlatformOwner>();
        for (var routeId : depot.routeIds) {
            Route route = ClientData.DATA_CACHE.routeIdMap.get(routeId);
            if (route == null) {
                continue;
            }
            for (var routePlatform : route.platformIds) {
                routePlatforms.add(new RoutePlatformOwner(route, routePlatform.platformId));
            }
        }
        if (routePlatforms.isEmpty()) {
            return List.of();
        }
        var assignedPaths = new ArrayList<AssignedPath>();
        var currentRoute = routePlatforms.getFirst().route();
        var platformIndex = depotPath.mainPath().getFirst().pathData().savedRailBaseId == 0 ? 1 : 0;
        for (var pathWithDistance : depotPath.mainPath()) {
            var pathData = pathWithDistance.pathData();
            assignedPaths.add(new AssignedPath(currentRoute, pathData));
            if (pathData.savedRailBaseId == 0) {
                continue;
            }
            if (platformIndex >= routePlatforms.size()
                    || routePlatforms.get(platformIndex).platformId() != pathData.savedRailBaseId) {
                return List.of();
            }
            currentRoute = routePlatforms.get(platformIndex).route();
            if (platformIndex + 1 < routePlatforms.size()
                    && routePlatforms.get(platformIndex).platformId() == routePlatforms.get(platformIndex + 1).platformId()) {
                platformIndex++;
                currentRoute = routePlatforms.get(platformIndex).route();
            }
            platformIndex++;
        }
        return assignedPaths;
    }

    private static @Nullable RouteKey routeKey(Route route) {
        var stops = new ArrayList<StopKey>();
        StopKey previousStop = null;
        for (var routePlatform : route.platformIds) {
            Station station = ClientData.DATA_CACHE.platformIdToStation.get(routePlatform.platformId);
            var stop = station == null
                    ? new StopKey(false, routePlatform.platformId)
                    : new StopKey(true, station.id);
            if (stop.equals(previousStop)) {
                continue;
            }
            stops.add(stop);
            previousStop = stop;
        }
        if (stops.size() < 2) {
            return null;
        }
        var forwardStops = List.copyOf(stops);
        var reverseStops = new ArrayList<>(forwardStops);
        Collections.reverse(reverseStops);
        var canonicalStops = compareStops(forwardStops, reverseStops) <= 0
                ? forwardStops
                : List.copyOf(reverseStops);
        return new RouteKey(route.color, route.transportMode, canonicalStops);
    }

    private static int compareStops(List<StopKey> first, List<StopKey> second) {
        for (var index = 0; index < first.size(); index++) {
            var stationComparison = Boolean.compare(first.get(index).station(), second.get(index).station());
            if (stationComparison != 0) {
                return stationComparison;
            }
            var idComparison = Long.compare(first.get(index).id(), second.get(index).id());
            if (idComparison != 0) {
                return idComparison;
            }
        }
        return 0;
    }

    private static boolean isTurnBack(PathData pathData) {
        return pathData.rail.railType == RailType.TURN_BACK
                || pathData.dwellTime == 1 && pathData.rail.railType.speedLimit != 2;
    }

    private static void drawRail(PathData pathData, int color, VertexConsumer buffer, PoseStack.Pose pose) {
        var length = pathData.rail.getLength();
        if (!Double.isFinite(length) || length <= 0) {
            return;
        }
        var segmentCount = Math.max(1, (int) Math.ceil(length / SAMPLE_DISTANCE));
        for (var index = 0; index < segmentCount; index++) {
            var start = pathData.rail.getPosition(length * index / segmentCount);
            var end = pathData.rail.getPosition(length * (index + 1) / segmentCount);
            GridRenderer.tryDrawLine3D(
                    buffer,
                    pose,
                    (float) start.x,
                    (float) start.y,
                    (float) start.z,
                    (float) end.x,
                    (float) end.y,
                    (float) end.z,
                    color,
                    color
            );
        }
    }

    private record StopKey(boolean station, long id) {
    }

    private record RouteKey(int color, TransportMode transportMode, List<StopKey> stops) {
    }

    private record RoutePlatformOwner(Route route, long platformId) {
    }

    private record AssignedPath(Route route, PathData pathData) {
    }

    private record RenderedRail(RouteKey routeKey, UUID railProduct) {
    }
}
