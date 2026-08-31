package com.plot.plugin.road.pipeline.facility;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;

import java.util.ArrayList;
import java.util.List;

/**
 * 路口/端点附近的附属设施裁剪：跳过距节点一定距离内的采样点。
 */
public final class StationFacilityJunctionTrim {

    private static final double EPSILON = 1e-9;

    private StationFacilityJunctionTrim() {
    }

    public record FacilityEndpointTrim(double skipStart, double skipEnd) {

        public static final FacilityEndpointTrim NONE = new FacilityEndpointTrim(0.0, 0.0);

        public boolean shouldPlace(double localDistance, double edgeLength) {
            if (!Double.isFinite(localDistance) || !Double.isFinite(edgeLength) || edgeLength < 0.0) {
                return false;
            }
            if (localDistance + EPSILON < skipStart) {
                return false;
            }
            if (localDistance > edgeLength - skipEnd + EPSILON) {
                return false;
            }
            return true;
        }
    }

    public static FacilityEndpointTrim forEdge(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            ResolvedCrossSection crossSection,
            RoadSystemConfig config,
            double unitsPerBlock) {
        if (network == null || edge == null || crossSection == null) {
            return FacilityEndpointTrim.NONE;
        }
        double edgeLength = edge.getLength();
        if (edgeLength <= EPSILON) {
            return FacilityEndpointTrim.NONE;
        }

        RoadNode startNode = network.getNode(edge.getStartNodeId());
        RoadNode endNode = network.getNode(edge.getEndNodeId());
        double skipStart = 0.0;
        double skipEnd = 0.0;

        if (startNode != null && shouldTrimAtEndpoint(network, road, edge, startNode)) {
            skipStart = skipDistanceAtNode(network, startNode, edge, crossSection, config, unitsPerBlock);
        }
        if (endNode != null && shouldTrimAtEndpoint(network, road, edge, endNode)) {
            skipEnd = skipDistanceAtNode(network, endNode, edge, crossSection, config, unitsPerBlock);
        }

        skipStart = clampSkip(skipStart, edgeLength);
        skipEnd = clampSkip(skipEnd, edgeLength);
        if (skipStart + skipEnd > edgeLength - EPSILON) {
            double half = edgeLength / 2.0;
            skipStart = Math.min(skipStart, half);
            skipEnd = Math.min(skipEnd, half);
        }
        return new FacilityEndpointTrim(skipStart, skipEnd);
    }

    static boolean shouldTrimAtEndpoint(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            RoadNode node) {
        if (node == null) {
            return false;
        }
        int degree = node.getDegree();
        if (degree >= 3) {
            return true;
        }
        if (degree <= 1) {
            return true;
        }
        return !isSameRoadInternalConnection(network, road, node);
    }

    static boolean isSameRoadInternalConnection(RoadNetwork network, Road road, RoadNode node) {
        if (network == null || road == null || node == null || node.getDegree() != 2) {
            return false;
        }
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge connected = network.getEdge(edgeId);
            if (connected == null || connected.getRoadId() == null) {
                return false;
            }
            if (!connected.getRoadId().equals(road.getId())) {
                return false;
            }
        }
        return true;
    }

    private static double skipDistanceAtNode(
            RoadNetwork network,
            RoadNode node,
            RoadEdge edge,
            ResolvedCrossSection crossSection,
            RoadSystemConfig config,
            double unitsPerBlock) {
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        double baseSkipBlocks = Math.max(
            1.0,
            crossSection.carriagewayWidth + crossSection.outerBandBlockCount() + 0.5);

        if (node.isJunction()) {
            List<RoadEdge> connected = connectedEdges(network, node);
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
            baseSkipBlocks = Math.max(baseSkipBlocks, junctionRadiusBlocks + cornerBlocks + 1.0);
        }

        return baseSkipBlocks * scale;
    }

    private static List<RoadEdge> connectedEdges(RoadNetwork network, RoadNode node) {
        List<RoadEdge> edges = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null) {
                edges.add(edge);
            }
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
