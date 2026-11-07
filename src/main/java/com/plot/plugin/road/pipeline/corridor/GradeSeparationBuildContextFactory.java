package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.crossing.GradeSeparationClearance;
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
        List<GradeSeparationBuildContext.ProtectedUnderpassZone> elevatedZones = new ArrayList<>();
        collectElevatedZone(network, edge, network.getNode(edge.getStartNodeId()), networkNodeElevations, config,
            elevatedRoadIdResolver, elevatedZones);
        collectElevatedZone(network, edge, network.getNode(edge.getEndNodeId()), networkNodeElevations, config,
            elevatedRoadIdResolver, elevatedZones);
        if (!elevatedZones.isEmpty()) {
            return new GradeSeparationBuildContext(true, elevatedZones);
        }
        List<GradeSeparationBuildContext.ProtectedUnderpassZone> lowerZones = new ArrayList<>();
        collectLowerZone(network, edge, network.getNode(edge.getStartNodeId()), networkNodeElevations, config,
            elevatedRoadIdResolver, lowerZones);
        collectLowerZone(network, edge, network.getNode(edge.getEndNodeId()), networkNodeElevations, config,
            elevatedRoadIdResolver, lowerZones);
        if (!lowerZones.isEmpty()) {
            return new GradeSeparationBuildContext(false, lowerZones);
        }
        return GradeSeparationBuildContext.NONE;
    }

    @FunctionalInterface
    public interface ElevatedRoadIdResolver {
        String resolve(RoadNode node, RoadNetwork network);
    }

    private static void collectElevatedZone(
            RoadNetwork network,
            RoadEdge edge,
            RoadNode node,
            Map<String, Integer> networkNodeElevations,
            RoadSystemConfig config,
            ElevatedRoadIdResolver elevatedRoadIdResolver,
            List<GradeSeparationBuildContext.ProtectedUnderpassZone> zones) {
        if (node == null || node.getDegree() < 3) {
            return;
        }
        if (node.isStackGradeSeparated()) {
            if (!node.isStackUpperEdge(edge.getId())) {
                return;
            }
            collectStackUnderpassZone(network, node, networkNodeElevations, config, zones);
            return;
        }
        if (!node.isGradeSeparated()) {
            return;
        }
        String elevatedRoadId = elevatedRoadIdResolver != null
            ? elevatedRoadIdResolver.resolve(node, network)
            : node.getElevatedRoadId();
        if (elevatedRoadId == null || !elevatedRoadId.equals(edge.getRoadId())) {
            return;
        }
        addUnderpassZone(network, node, networkNodeElevations, config, elevatedRoadId, zones);
    }

    private static void collectLowerZone(
            RoadNetwork network,
            RoadEdge edge,
            RoadNode node,
            Map<String, Integer> networkNodeElevations,
            RoadSystemConfig config,
            ElevatedRoadIdResolver elevatedRoadIdResolver,
            List<GradeSeparationBuildContext.ProtectedUnderpassZone> zones) {
        if (node == null || node.getDegree() < 3 || !node.isGradeSeparated()) {
            return;
        }
        if (node.isStackGradeSeparated()) {
            // STACK 下层也需要净空上界，避免走廊清障挖穿上层底板。
            if (node.isStackUpperEdge(edge.getId())) {
                return;
            }
            collectStackUnderpassZone(network, node, networkNodeElevations, config, zones);
            return;
        }
        String elevatedRoadId = elevatedRoadIdResolver != null
            ? elevatedRoadIdResolver.resolve(node, network)
            : node.getElevatedRoadId();
        if (elevatedRoadId != null && elevatedRoadId.equals(edge.getRoadId())) {
            return;
        }
        // 下层净空上界须覆盖上层路面投影；斜交时仅 underpass 臂多边形会漏柱，
        // 导致 openRoadClearanceTops 顶到 deckY 并清掉承重层高度。
        addUnderpassZone(network, node, networkNodeElevations, config, elevatedRoadId, zones,
            /*includeElevatedInPolygon*/ true);
    }

    private static void addUnderpassZone(
            RoadNetwork network,
            RoadNode node,
            Map<String, Integer> networkNodeElevations,
            RoadSystemConfig config,
            String elevatedRoadId,
            List<GradeSeparationBuildContext.ProtectedUnderpassZone> zones) {
        addUnderpassZone(network, node, networkNodeElevations, config, elevatedRoadId, zones, false);
    }

    private static void addUnderpassZone(
            RoadNetwork network,
            RoadNode node,
            Map<String, Integer> networkNodeElevations,
            RoadSystemConfig config,
            String elevatedRoadId,
            List<GradeSeparationBuildContext.ProtectedUnderpassZone> zones,
            boolean includeElevatedInPolygon) {
        Integer underpassY = networkNodeElevations.get(node.getId());
        if (underpassY == null) {
            return;
        }
        double clearance = GradeSeparationClearance.forNode(
            node.getCrossingClearance(), node, network, elevatedRoadId, config);
        int floorY = underpassY + (int) Math.ceil(Math.max(1.0, clearance));
        List<Vec2d> polygon = junctionPolygon(
            network, node, config, includeElevatedInPolygon ? null : elevatedRoadId);
        if (polygon.size() >= 3) {
            zones.add(new GradeSeparationBuildContext.ProtectedUnderpassZone(polygon, floorY, underpassY));
        }
    }

    private static void collectStackUnderpassZone(
            RoadNetwork network,
            RoadNode node,
            Map<String, Integer> networkNodeElevations,
            RoadSystemConfig config,
            List<GradeSeparationBuildContext.ProtectedUnderpassZone> zones) {
        Integer underpassY = networkNodeElevations.get(node.getId());
        if (underpassY == null) {
            return;
        }
        String roadId = null;
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null && edge.getRoadId() != null) {
                roadId = edge.getRoadId();
                break;
            }
        }
        double clearance = GradeSeparationClearance.resolve(
            node.getStackCrossingClearance(),
            roadId != null ? network.getRoad(roadId) : null,
            config);
        int floorY = underpassY + (int) Math.ceil(Math.max(1.0, clearance));
        List<Vec2d> polygon = stackJunctionPolygon(network, node, config);
        if (polygon.size() >= 3) {
            zones.add(new GradeSeparationBuildContext.ProtectedUnderpassZone(polygon, floorY, underpassY));
        }
    }

    private static List<Vec2d> stackJunctionPolygon(
            RoadNetwork network,
            RoadNode node,
            RoadSystemConfig config) {
        List<RoadEdge> connected = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge connectedEdge = network.getEdge(edgeId);
            if (connectedEdge != null) {
                connected.add(connectedEdge);
            }
        }
        if (connected.isEmpty()) {
            return List.of();
        }
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            connected,
            edge -> RoadDimensionUtils.halfExtentFromCenter(
                RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()),
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS);
        // 净空保护区用锐角轮廓：路缘倒圆角不得缩小立交净空上界覆盖。
        return RoadJunctionGeometry.buildJunctionFillPolygon(
            node.getId(),
            connected,
            edge -> RoadDimensionUtils.halfExtentFromCenter(
                RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()),
            junctionRadius,
            0.0,
            RoadJunctionCenterlineResolver.forNetwork(network));
    }

    private static List<Vec2d> junctionPolygon(
            RoadNetwork network,
            RoadNode node,
            RoadSystemConfig config,
            String elevatedRoadId) {
        List<RoadEdge> underpass = new ArrayList<>();
        List<RoadEdge> allArms = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge connectedEdge = network.getEdge(edgeId);
            if (connectedEdge == null) {
                continue;
            }
            allArms.add(connectedEdge);
            if (elevatedRoadId != null
                    && connectedEdge.getRoadId() != null
                    && elevatedRoadId.equals(connectedEdge.getRoadId())) {
                continue;
            }
            underpass.add(connectedEdge);
        }
        List<Vec2d> polygon = fillPolygonForEdges(network, node, config, underpass);
        if (polygon.size() >= 3) {
            return polygon;
        }
        // 两臂下穿区域式为空时，用全进口足迹保证上跨边仍被标为 elevatedRoadEdge（禁开敞填方）
        return fillPolygonForEdges(network, node, config, allArms);
    }

    private static List<Vec2d> fillPolygonForEdges(
            RoadNetwork network,
            RoadNode node,
            RoadSystemConfig config,
            List<RoadEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return List.of();
        }
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            edges,
            edge -> RoadDimensionUtils.halfExtentFromCenter(
                RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()),
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS);
        // 净空/保护范围保持锐角足迹，与铺装倒圆角解耦。
        return RoadJunctionGeometry.buildJunctionFillPolygon(
            node.getId(),
            edges,
            edge -> RoadDimensionUtils.halfExtentFromCenter(
                RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()),
            junctionRadius,
            0.0,
            RoadJunctionCenterlineResolver.forNetwork(network));
    }
}
