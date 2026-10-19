package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.facility.StationFacilityJunctionTrim;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Computes how far an edge corridor should stay away from junction nodes.
 */
public final class JunctionCorridorTrim {
    private static final double EPSILON = 1e-9;

    private JunctionCorridorTrim() {
    }

    public static JunctionCorridorBuildContext forEdge(
            RoadNetwork network,
            RoadEdge edge,
            ResolvedCrossSection crossSection,
            RoadSystemConfig config,
            double unitsPerBlock) {
        if (network == null || edge == null || crossSection == null) {
            return JunctionCorridorBuildContext.NONE;
        }
        double edgeLength = edge.getLength();
        if (edgeLength <= EPSILON) {
            return JunctionCorridorBuildContext.NONE;
        }

        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        RoadNode startNode = network.getNode(edge.getStartNodeId());
        RoadNode endNode = network.getNode(edge.getEndNodeId());
        double skipStart = 0.0;
        double skipEnd = 0.0;
        List<List<Vec2d>> polygons = new ArrayList<>();

        if (shouldTrimAtEndpoint(network, edge, startNode)) {
            skipStart = skipDistanceAtNode(network, startNode, crossSection, config) * scale;
            exclusionPolygon(network, startNode, config).ifPresent(polygons::add);
        }
        if (shouldTrimAtEndpoint(network, edge, endNode)) {
            skipEnd = skipDistanceAtNode(network, endNode, crossSection, config) * scale;
            exclusionPolygon(network, endNode, config).ifPresent(polygons::add);
        }

        skipStart = clampSkip(skipStart, edgeLength);
        skipEnd = clampSkip(skipEnd, edgeLength);
        if (skipStart + skipEnd > edgeLength - EPSILON) {
            double half = edgeLength / 2.0;
            skipStart = Math.min(skipStart, half);
            skipEnd = Math.min(skipEnd, half);
        }
        return new JunctionCorridorBuildContext(
            new StationFacilityJunctionTrim.FacilityEndpointTrim(skipStart, skipEnd),
            List.copyOf(polygons));
    }

    static boolean shouldTrimAtEndpoint(RoadNetwork network, RoadEdge edge, RoadNode node) {
        if (node == null || node.getDegree() < 3) {
            return false;
        }
        if (node.isStackGradeSeparated()) {
            return !node.isStackUpperEdge(edge.getId());
        }
        if (node.isGradeSeparated()) {
            String elevatedRoadId = node.getElevatedRoadId();
            String edgeRoadId = edge.getRoadId();
            return elevatedRoadId == null || !elevatedRoadId.equals(edgeRoadId);
        }
        return true;
    }

    private static Optional<List<Vec2d>> exclusionPolygon(
            RoadNetwork network,
            RoadNode node,
            RoadSystemConfig config) {
        String elevatedRoadId = node.isGradeSeparated() ? node.getElevatedRoadId() : null;
        List<RoadEdge> connected = connectedEdges(network, node, elevatedRoadId);
        if (connected.isEmpty()) {
            return Optional.empty();
        }
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            connected,
            edge -> RoadDimensionUtils.halfExtentFromCenter(
                RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()),
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS);
        double cornerRadius = node.getEffectiveCornerRadius(config.getDefaultCornerRadius());
        List<Vec2d> polygon = RoadJunctionGeometry.buildJunctionFillPolygon(
            node.getId(),
            connected,
            edge -> RoadDimensionUtils.halfExtentFromCenter(
                RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()),
            junctionRadius,
            cornerRadius,
            RoadJunctionCenterlineResolver.forNetwork(network));
        return polygon.size() >= 3 ? Optional.of(polygon) : Optional.empty();
    }

    private static double skipDistanceAtNode(
            RoadNetwork network,
            RoadNode node,
            ResolvedCrossSection crossSection,
            RoadSystemConfig config) {
        double baseSkipBlocks = Math.max(
            1.0,
            crossSection.carriagewayWidth + crossSection.outerBandBlockCount() + 0.5);
        List<RoadEdge> connected = connectedEdges(
            network,
            node,
            node.isGradeSeparated() ? node.getElevatedRoadId() : null);
        double maxHalfWidth = 0.0;
        for (RoadEdge connectedEdge : connected) {
            maxHalfWidth = Math.max(
                maxHalfWidth,
                RoadModelUtils.getEffectiveWidth(network, connectedEdge, config) / 2.0);
        }
        double junctionRadiusBlocks = Math.max(
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS,
            maxHalfWidth + 1.0);
        double cornerBlocks = node.getEffectiveCornerRadius(config.getDefaultCornerRadius());
        return Math.max(baseSkipBlocks, junctionRadiusBlocks + cornerBlocks + 1.0);
    }

    private static List<RoadEdge> connectedEdges(
            RoadNetwork network,
            RoadNode node,
            String excludedRoadId) {
        List<RoadEdge> edges = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            if (excludedRoadId != null
                    && edge.getRoadId() != null
                    && excludedRoadId.equals(edge.getRoadId())) {
                continue;
            }
            edges.add(edge);
        }
        return edges;
    }

    private static double clampSkip(double skip, double edgeLength) {
        if (!Double.isFinite(skip) || skip <= 0.0) {
            return 0.0;
        }
        return Math.min(skip, edgeLength);
    }
}
