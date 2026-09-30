package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.IntersectionResult;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.graph.RoadGraphEdits;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.station.SegmentStation;

import java.util.List;
import java.util.Optional;

/**
 * 在 snapshot / 生成前将已注册 {@link RoadCrossing} 临时物化为共享节点拓扑，兼容旧 junction 管线。
 */
public final class RoadCrossingMaterializer {
    private static final double NODE_TOLERANCE = RoadNetworkBuilder.NODE_TOLERANCE;

    private RoadCrossingMaterializer() {
    }

    public static RoadNetwork materializeForSnapshot(RoadNetwork source) {
        if (source == null) {
            return new RoadNetwork();
        }
        if (source.getCrossings().isEmpty()) {
            return source.snapshot();
        }
        RoadNetwork snapshot = source.snapshot();
        for (RoadCrossing crossing : source.getCrossings().values()) {
            materializeCrossing(snapshot, crossing);
        }
        return snapshot;
    }

    public static IntersectionResult materializeInPlace(RoadNetwork network) {
        if (network == null || network.getCrossings().isEmpty()) {
            return IntersectionResult.COMPLETE;
        }
        for (RoadCrossing crossing : List.copyOf(network.getCrossings().values())) {
            materializeCrossing(network, crossing);
        }
        return IntersectionResult.COMPLETE;
    }

    private static void materializeCrossing(RoadNetwork network, RoadCrossing crossing) {
        Vec2d position = crossing.position();
        if (position == null) {
            return;
        }
        RoadNode junction = findOrCreateNode(network, position);
        materializeRoadAtCrossing(network, crossing.roadAId(), crossing.stationA(), junction, position);
        materializeRoadAtCrossing(network, crossing.roadBId(), crossing.stationB(), junction, position);
    }

    private static void materializeRoadAtCrossing(
            RoadNetwork network,
            String roadId,
            double station,
            RoadNode junction,
            Vec2d position) {
        Road road = network.getRoad(roadId);
        if (road == null) {
            return;
        }
        Optional<SegmentStation> resolved = RoadStationing.resolve(network, road, station);
        if (resolved.isEmpty()) {
            return;
        }
        String edgeId = resolved.get().segmentId();
        RoadEdge edge = network.getEdge(edgeId);
        if (edge == null) {
            return;
        }
        if (isNearEdgeEndpoint(edge, position)) {
            connectEdgeEndpointToNode(network, edge, position, junction);
        } else {
            RoadGraphEdits.of(network).splitEdgeAtNode(
                edgeId, junction.getId(), position, NODE_TOLERANCE);
        }
    }

    private static RoadNode findOrCreateNode(RoadNetwork network, Vec2d position) {
        for (RoadNode node : network.getNodes().values()) {
            if (RoadGeometryUtils.pointsNear(node.getPosition(), position, NODE_TOLERANCE)) {
                return node;
            }
        }
        return network.createNode(position.copy());
    }

    private static boolean isNearEdgeEndpoint(RoadEdge edge, Vec2d point) {
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.isEmpty()) {
            return true;
        }
        return RoadGeometryUtils.pointsNear(points.getFirst(), point, NODE_TOLERANCE)
            || RoadGeometryUtils.pointsNear(points.getLast(), point, NODE_TOLERANCE);
    }

    private static void connectEdgeEndpointToNode(
            RoadNetwork network,
            RoadEdge edge,
            Vec2d intersection,
            RoadNode junctionNode) {
        String endpointNodeId = findEndpointNodeId(edge, intersection);
        if (endpointNodeId == null || endpointNodeId.equals(junctionNode.getId())) {
            return;
        }
        mergeEdgeEndpointToNode(network, edge, endpointNodeId, junctionNode.getId());
    }

    private static String findEndpointNodeId(RoadEdge edge, Vec2d intersection) {
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.isEmpty()) {
            return null;
        }
        if (RoadGeometryUtils.pointsNear(points.getFirst(), intersection, NODE_TOLERANCE)) {
            return edge.getStartNodeId();
        }
        if (RoadGeometryUtils.pointsNear(points.getLast(), intersection, NODE_TOLERANCE)) {
            return edge.getEndNodeId();
        }
        return null;
    }

    private static void mergeEdgeEndpointToNode(
            RoadNetwork network,
            RoadEdge edge,
            String oldNodeId,
            String newNodeId) {
        if (oldNodeId.equals(newNodeId)) {
            return;
        }
        RoadNode oldNode = network.getNode(oldNodeId);
        RoadNode newNode = network.getNode(newNodeId);
        if (oldNode == null || newNode == null) {
            return;
        }
        boolean relinked = false;
        if (edge.getStartNodeId().equals(oldNodeId)) {
            oldNode.removeEdge(edge.getId());
            edge.setStartNodeId(newNodeId);
            newNode.addEdge(edge.getId());
            relinked = true;
        } else if (edge.getEndNodeId().equals(oldNodeId)) {
            oldNode.removeEdge(edge.getId());
            edge.setEndNodeId(newNodeId);
            newNode.addEdge(edge.getId());
            relinked = true;
        }
        if (relinked && oldNode.getDegree() == 0) {
            network.removeNode(oldNodeId);
        }
    }
}
