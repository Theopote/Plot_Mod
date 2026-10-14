package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link GradeSeparationBuildContext} for elevated edges near grade-separated nodes.
 */
public final class GradeSeparationBuildContextFactory {
    private GradeSeparationBuildContextFactory() {
    }

    public static GradeSeparationBuildContext forEdge(
            RoadNetwork network,
            RoadEdge edge,
            Map<String, Integer> networkNodeElevations,
            RoadSystemConfig config,
            ElevatedRoadIdResolver elevatedRoadIdResolver) {
        if (network == null || edge == null || config == null || networkNodeElevations == null) {
            return GradeSeparationBuildContext.NONE;
        }
        List<GradeSeparationBuildContext.ProtectedUnderpassZone> zones = new ArrayList<>();
        collectZone(network, edge, network.getNode(edge.getStartNodeId()), networkNodeElevations, config,
            elevatedRoadIdResolver, zones);
        collectZone(network, edge, network.getNode(edge.getEndNodeId()), networkNodeElevations, config,
            elevatedRoadIdResolver, zones);
        if (zones.isEmpty()) {
            return GradeSeparationBuildContext.NONE;
        }
        return new GradeSeparationBuildContext(true, zones);
    }

    @FunctionalInterface
    public interface ElevatedRoadIdResolver {
        String resolve(RoadNode node, RoadNetwork network);
    }

    private static void collectZone(
            RoadNetwork network,
            RoadEdge edge,
            RoadNode node,
            Map<String, Integer> networkNodeElevations,
            RoadSystemConfig config,
            ElevatedRoadIdResolver elevatedRoadIdResolver,
            List<GradeSeparationBuildContext.ProtectedUnderpassZone> zones) {
        if (node == null || !node.isGradeSeparated() || node.getDegree() < 3) {
            return;
        }
        String elevatedRoadId = elevatedRoadIdResolver != null
            ? elevatedRoadIdResolver.resolve(node, network)
            : node.getElevatedRoadId();
        if (elevatedRoadId == null || !elevatedRoadId.equals(edge.getRoadId())) {
            return;
        }
        Integer underpassY = networkNodeElevations.get(node.getId());
        if (underpassY == null) {
            return;
        }
        double clearance = node.getCrossingClearance() != null
            ? node.getCrossingClearance()
            : config.getDefaultCrossingClearance();
        int floorY = underpassY + (int) Math.ceil(Math.max(1.0, clearance));
        List<Vec2d> polygon = junctionPolygon(network, node, config, elevatedRoadId);
        if (polygon.size() >= 3) {
            zones.add(new GradeSeparationBuildContext.ProtectedUnderpassZone(polygon, floorY));
        }
    }

    private static List<Vec2d> junctionPolygon(
            RoadNetwork network,
            RoadNode node,
            RoadSystemConfig config,
            String elevatedRoadId) {
        List<RoadEdge> connected = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge connectedEdge = network.getEdge(edgeId);
            if (connectedEdge == null) {
                continue;
            }
            if (elevatedRoadId != null
                    && connectedEdge.getRoadId() != null
                    && elevatedRoadId.equals(connectedEdge.getRoadId())) {
                continue;
            }
            connected.add(connectedEdge);
        }
        if (connected.isEmpty()) {
            return List.of();
        }
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            connected,
            edge -> RoadModelUtils.getEffectiveWidth(network, edge, config) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS);
        double cornerRadius = node.getEffectiveCornerRadius(config.getDefaultCornerRadius());
        return RoadJunctionGeometry.buildJunctionFillPolygon(
            node.getId(),
            connected,
            edge -> RoadModelUtils.getEffectiveWidth(network, edge, config) / 2.0,
            junctionRadius,
            cornerRadius,
            RoadJunctionCenterlineResolver.forNetwork(network));
    }
}
