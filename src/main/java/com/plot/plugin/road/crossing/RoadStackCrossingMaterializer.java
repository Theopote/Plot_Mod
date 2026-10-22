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
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.station.SegmentStation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * 在 snapshot / 生成前将 {@link RoadStackCrossing} 物化为四臂路口节点。
 */
public final class RoadStackCrossingMaterializer {
    private static final double NODE_TOLERANCE = RoadNetworkBuilder.NODE_TOLERANCE;

    private RoadStackCrossingMaterializer() {
    }

    public static void materializeAll(RoadNetwork network, Collection<RoadStackCrossing> stackCrossings) {
        if (network == null || stackCrossings == null || stackCrossings.isEmpty()) {
            return;
        }
        for (RoadStackCrossing stackCrossing : stackCrossings) {
            materializeStackCrossing(network, stackCrossing);
        }
    }

    public static IntersectionResult materializeInPlace(RoadNetwork network) {
        if (network == null || network.getStackCrossings().isEmpty()) {
            return IntersectionResult.COMPLETE;
        }
        materializeAll(network, network.getStackCrossings().values());
        return IntersectionResult.COMPLETE;
    }

    private static void materializeStackCrossing(RoadNetwork network, RoadStackCrossing stackCrossing) {
        Vec2d position = stackCrossing.position();
        if (position == null || stackCrossing.roadId() == null) {
            return;
        }
        RoadNode junction = findOrCreateNode(network, position);
        materializeRoadAtStackStation(network, stackCrossing.roadId(), stackCrossing.stationA(), junction, position);
        materializeRoadAtStackStation(network, stackCrossing.roadId(), stackCrossing.stationB(), junction, position);
        breakJunctionSelfLoops(network, junction);
        applyStackDesignToJunction(network, stackCrossing, junction);
    }

    private static void materializeRoadAtStackStation(
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
        RoadEdge edge = network.getEdge(resolved.get().segmentId());
        if (edge == null) {
            return;
        }
        int interiorSegment = findInteriorPassSegmentIndex(edge.getCenterlinePoints(), position);
        if (interiorSegment >= 0) {
            Vec2d projected = RoadGeometryUtils.projectPointOnSegment(
                edge.getCenterlinePoints().get(interiorSegment),
                edge.getCenterlinePoints().get(interiorSegment + 1),
                position);
            RoadGraphEdits.of(network).splitEdgeAtInteriorSegment(
                edge.getId(),
                junction.getId(),
                interiorSegment,
                projected,
                NODE_TOLERANCE);
            return;
        }
        if (isNearEdgeEndpoint(edge, position)) {
            connectEdgeEndpointToNode(network, edge, position, junction);
        }
    }

    /**
     * 二次穿越会在路口形成自环；在首个内部折线顶点处打断，得到四臂拓扑。
     */
    private static void breakJunctionSelfLoops(RoadNetwork network, RoadNode junction) {
        List<String> edgeIds = new ArrayList<>(junction.getConnectedEdgeIds());
        for (String edgeId : edgeIds) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            if (!edge.getStartNodeId().equals(junction.getId())
                    || !edge.getEndNodeId().equals(junction.getId())) {
                continue;
            }
            Optional<Vec2d> breakVertex = findSelfLoopBreakVertex(edge.getCenterlinePoints(), junction.getPosition());
            if (breakVertex.isEmpty()) {
                continue;
            }
            RoadNode breakNode = findOrCreateNode(network, breakVertex.get());
            RoadGraphEdits.of(network).splitEdgeAtNode(
                edge.getId(),
                breakNode.getId(),
                breakVertex.get(),
                NODE_TOLERANCE);
        }
    }

    private static Optional<Vec2d> findSelfLoopBreakVertex(List<Vec2d> points, Vec2d junctionPosition) {
        if (points == null || points.size() < 3 || junctionPosition == null) {
            return Optional.empty();
        }
        for (int i = 1; i < points.size() - 1; i++) {
            Vec2d candidate = points.get(i);
            if (!RoadGeometryUtils.pointsNear(candidate, junctionPosition, NODE_TOLERANCE)) {
                return Optional.of(candidate.copy());
            }
        }
        return Optional.empty();
    }

    private static int findInteriorPassSegmentIndex(List<Vec2d> points, Vec2d position) {
        if (points == null || points.size() < 2 || position == null) {
            return -1;
        }
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get(i + 1);
            Vec2d projected = RoadGeometryUtils.projectPointOnSegment(start, end, position);
            if (projected.distance(position) > NODE_TOLERANCE) {
                continue;
            }
            boolean atPolyStart = i == 0 && RoadGeometryUtils.pointsNear(start, position, NODE_TOLERANCE);
            boolean atPolyEnd = i == points.size() - 2
                && RoadGeometryUtils.pointsNear(end, position, NODE_TOLERANCE);
            if (atPolyStart || atPolyEnd) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private static void applyStackDesignToJunction(
            RoadNetwork network,
            RoadStackCrossing stackCrossing,
            RoadNode junction) {
        junction.clearStackCrossingMetadata();
        if (stackCrossing.sharedElevation() != null) {
            junction.setManualElevation(
                stackCrossing.sharedElevation(),
                com.plot.plugin.road.model.ManualElevationOrigin.CROSSING);
        }
        if (!stackCrossing.isGradeSeparated()) {
            return;
        }
        junction.setStackCrossingId(stackCrossing.id());
        junction.setStackGradeSeparated(true);
        if (stackCrossing.crossingClearance() != null) {
            junction.setStackCrossingClearance(stackCrossing.crossingClearance());
        }
        assignStackArmRoles(network, stackCrossing, junction);
    }

    private static void assignStackArmRoles(
            RoadNetwork network,
            RoadStackCrossing stackCrossing,
            RoadNode junction) {
        String roadId = stackCrossing.roadId();
        double lowerStation = stackCrossing.resolvedLowerStation();
        double upperStation = stackCrossing.resolvedUpperStation();
        Road road = network.getRoad(roadId);
        String junctionId = junction.getId();
        Optional<String> entryNodeId = RoadStationing.chainEntryNodeId(network, road);
        Optional<String> exitNodeId = RoadStationing.chainExitNodeId(network, road);
        for (String edgeId : junction.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null || !roadId.equals(edge.getRoadId())) {
                continue;
            }
            boolean lowerArm = isLowerStackArm(
                network,
                road,
                edge,
                junctionId,
                lowerStation,
                upperStation,
                entryNodeId.orElse(null),
                exitNodeId.orElse(null));
            if (lowerArm) {
                junction.addStackLowerEdgeId(edgeId);
            } else {
                junction.addStackUpperEdgeId(edgeId);
            }
        }
    }

    private static boolean isLowerStackArm(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            String junctionId,
            double lowerStation,
            double upperStation,
            String entryNodeId,
            String exitNodeId) {
        String farNodeId = oppositeEndpoint(edge, junctionId);
        if (farNodeId != null && farNodeId.equals(entryNodeId)) {
            return true;
        }
        if (farNodeId != null && farNodeId.equals(exitNodeId)) {
            return false;
        }
        if (edge.getStartNodeId().equals(junctionId)) {
            return true;
        }
        if (edge.getEndNodeId().equals(junctionId)) {
            return false;
        }
        Optional<Double> endpointStation = endpointStation(network, road, edge, junctionId);
        if (endpointStation.isEmpty()) {
            return true;
        }
        double station = endpointStation.get();
        return Math.abs(station - lowerStation) <= Math.abs(station - upperStation);
    }

    private static String oppositeEndpoint(RoadEdge edge, String nodeId) {
        if (edge.getStartNodeId().equals(nodeId)) {
            return edge.getEndNodeId();
        }
        if (edge.getEndNodeId().equals(nodeId)) {
            return edge.getStartNodeId();
        }
        return null;
    }

    private static Optional<Double> endpointStation(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            String nodeId) {
        if (road == null) {
            return Optional.empty();
        }
        if (RoadStationing.isStationable(network, road)) {
            OptionalDouble station = RoadStationing.stationAtNode(network, road, edge, nodeId);
            if (station.isPresent()) {
                return Optional.of(station.getAsDouble());
            }
        }
        return physicalEndpointStation(network, road, edge, nodeId);
    }

    private static Optional<Double> physicalEndpointStation(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            String nodeId) {
        Optional<OrientedRoadSegment> segment = RoadStationing.physicalOrientedSegment(
            network, road, edge.getId());
        if (segment.isEmpty()) {
            return Optional.empty();
        }
        OrientedRoadSegment oriented = segment.get();
        double instanceChainage;
        if (nodeId.equals(oriented.entryNodeId())) {
            instanceChainage = oriented.startStation();
        } else if (nodeId.equals(oriented.exitNodeId())) {
            instanceChainage = oriented.endStation();
        } else {
            return Optional.empty();
        }
        return Optional.of(RoadStationing.toCanonicalChainage(network, road, instanceChainage));
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
