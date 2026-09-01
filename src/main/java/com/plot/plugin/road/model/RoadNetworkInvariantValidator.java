package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 校验道路网络图模型不变量：拓扑、Road↔Edge 双向归属、坡度 override 范围、
 * Edge 中心线端点与节点位置一致等。
 */
public final class RoadNetworkInvariantValidator {

    /** Edge 中心线端点须与相连 {@link RoadNode} 位置一致（米）。 */
    public static final double GEOMETRY_ENDPOINT_TOLERANCE_METERS = 1e-3;

    private RoadNetworkInvariantValidator() {
    }

    public static RoadNetworkValidationResult validate(RoadNetwork network) {
        List<String> violations = new ArrayList<>();
        if (network == null) {
            violations.add("network is null");
            return new RoadNetworkValidationResult(false, List.copyOf(violations));
        }

        Map<String, RoadNode> nodes = network.getNodes();
        Map<String, RoadEdge> edges = network.getEdges();
        Map<String, Road> roads = network.getRoads();

        validateTopology(nodes, edges, violations);
        validateRoadEdgeMembership(edges, roads, violations);
        validateSlopeOverrides(edges, violations);
        validateEdgeGeometryEndpoints(nodes, edges, violations);

        return new RoadNetworkValidationResult(violations.isEmpty(), List.copyOf(violations));
    }

    /** 中心线端点与节点位置不一致的 Edge ID。 */
    public static Set<String> collectGeometryMismatchEdgeIds(RoadNetwork network) {
        Set<String> mismatched = new HashSet<>();
        if (network == null) {
            return mismatched;
        }
        List<String> violations = new ArrayList<>();
        validateEdgeGeometryEndpoints(network.getNodes(), network.getEdges(), violations);
        for (String violation : violations) {
            String edgeId = parseEdgeId(violation);
            if (edgeId != null) {
                mismatched.add(edgeId);
            }
        }
        return mismatched;
    }

    private static String parseEdgeId(String violation) {
        int edgeMarker = violation.indexOf(" edge ");
        if (edgeMarker < 0) {
            return null;
        }
        int start = edgeMarker + " edge ".length();
        int end = violation.indexOf(' ', start);
        return end < 0 ? violation.substring(start) : violation.substring(start, end);
    }

    /** 拓扑不一致的节点 ID（供节点列表「Invalid」筛选）。 */
    public static Set<String> collectInvalidNodeIds(RoadNetwork network) {
        Set<String> invalid = new HashSet<>();
        if (network == null) {
            return invalid;
        }
        collectInvalidNodeIds(network.getNodes(), network.getEdges(), invalid);
        return invalid;
    }

    private static void collectInvalidNodeIds(
            Map<String, RoadNode> nodes,
            Map<String, RoadEdge> edges,
            Set<String> invalid) {
        for (RoadNode node : nodes.values()) {
            for (String edgeId : node.getConnectedEdgeIds()) {
                RoadEdge edge = edges.get(edgeId);
                if (edge == null) {
                    invalid.add(node.getId());
                    continue;
                }
                if (!edge.getStartNodeId().equals(node.getId())
                        && !edge.getEndNodeId().equals(node.getId())) {
                    invalid.add(node.getId());
                }
            }
        }

        for (RoadEdge edge : edges.values()) {
            RoadNode start = nodes.get(edge.getStartNodeId());
            RoadNode end = nodes.get(edge.getEndNodeId());
            if (start != null && !start.getConnectedEdgeIds().contains(edge.getId())) {
                invalid.add(start.getId());
            }
            if (end != null && !end.getConnectedEdgeIds().contains(edge.getId())) {
                invalid.add(end.getId());
            }
        }
    }

    private static void validateTopology(
            Map<String, RoadNode> nodes,
            Map<String, RoadEdge> edges,
            List<String> violations) {
        for (RoadEdge edge : edges.values()) {
            if (edge.getStartNodeId() == null || !nodes.containsKey(edge.getStartNodeId())) {
                violations.add("edge " + edge.getId() + " references missing start node "
                    + edge.getStartNodeId());
            }
            if (edge.getEndNodeId() == null || !nodes.containsKey(edge.getEndNodeId())) {
                violations.add("edge " + edge.getId() + " references missing end node "
                    + edge.getEndNodeId());
            }
        }

        for (RoadNode node : nodes.values()) {
            for (String edgeId : node.getConnectedEdgeIds()) {
                RoadEdge edge = edges.get(edgeId);
                if (edge == null) {
                    violations.add("node " + node.getId() + " lists missing edge " + edgeId);
                    continue;
                }
                if (!edge.getStartNodeId().equals(node.getId())
                        && !edge.getEndNodeId().equals(node.getId())) {
                    violations.add("node " + node.getId() + " lists edge " + edgeId
                        + " but edge endpoints are " + edge.getStartNodeId()
                        + " and " + edge.getEndNodeId());
                }
            }
        }

        for (RoadEdge edge : edges.values()) {
            RoadNode start = nodes.get(edge.getStartNodeId());
            RoadNode end = nodes.get(edge.getEndNodeId());
            if (start != null && !start.getConnectedEdgeIds().contains(edge.getId())) {
                violations.add("edge " + edge.getId() + " start node " + start.getId()
                    + " missing connected edge entry");
            }
            if (end != null && !end.getConnectedEdgeIds().contains(edge.getId())) {
                violations.add("edge " + edge.getId() + " end node " + end.getId()
                    + " missing connected edge entry");
            }
        }
    }

    private static void validateRoadEdgeMembership(
            Map<String, RoadEdge> edges,
            Map<String, Road> roads,
            List<String> violations) {
        Map<String, String> segmentOwner = new HashMap<>();

        for (Road road : roads.values()) {
            for (String segmentId : road.getSegmentIds()) {
                if (!edges.containsKey(segmentId)) {
                    violations.add("road " + road.getId() + " lists missing segment " + segmentId);
                    continue;
                }
                String previousOwner = segmentOwner.put(segmentId, road.getId());
                if (previousOwner != null) {
                    violations.add("segment " + segmentId + " claimed by roads "
                        + previousOwner + " and " + road.getId());
                }
            }
        }

        for (RoadEdge edge : edges.values()) {
            String roadId = edge.getRoadId();
            if (roadId == null || roadId.isBlank()) {
                continue;
            }
            if (!roads.containsKey(roadId)) {
                violations.add("edge " + edge.getId() + " references missing road " + roadId);
                continue;
            }
            Road road = roads.get(roadId);
            if (!road.getSegmentIds().contains(edge.getId())) {
                violations.add("edge " + edge.getId() + " roadId is " + roadId
                    + " but road.segmentIds omits it");
            }
            String ownerFromRoad = segmentOwner.get(edge.getId());
            if (ownerFromRoad != null && !ownerFromRoad.equals(roadId)) {
                violations.add("edge " + edge.getId() + " roadId is " + roadId
                    + " but segmentIds owner is " + ownerFromRoad);
            }
        }

        Set<String> segmentsWithoutRoadId = new HashSet<>();
        for (Road road : roads.values()) {
            for (String segmentId : road.getSegmentIds()) {
                RoadEdge edge = edges.get(segmentId);
                if (edge == null) {
                    continue;
                }
                String roadId = edge.getRoadId();
                if (roadId == null || roadId.isBlank()) {
                    segmentsWithoutRoadId.add(segmentId);
                } else if (!roadId.equals(road.getId())) {
                    violations.add("road " + road.getId() + " lists segment " + segmentId
                        + " but edge.roadId is " + roadId);
                }
            }
        }
        for (String segmentId : segmentsWithoutRoadId) {
            violations.add("segment " + segmentId + " in road.segmentIds but edge.roadId is null");
        }
    }

    private static void validateEdgeGeometryEndpoints(
            Map<String, RoadNode> nodes,
            Map<String, RoadEdge> edges,
            List<String> violations) {
        for (RoadEdge edge : edges.values()) {
            List<Vec2d> points = edge.getCenterlinePoints();
            if (points == null || points.isEmpty()) {
                continue;
            }

            Vec2d first = points.getFirst();
            Vec2d last = points.getLast();

            RoadNode start = nodes.get(edge.getStartNodeId());
            if (start != null && start.getPosition() != null) {
                double distance = first.distance(start.getPosition());
                if (distance > GEOMETRY_ENDPOINT_TOLERANCE_METERS) {
                    violations.add(formatGeometryMismatch(
                        RoadNetworkViolationKind.EDGE_START_GEOMETRY_MISMATCH,
                        edge.getId(),
                        distance));
                }
            }

            RoadNode end = nodes.get(edge.getEndNodeId());
            if (end != null && end.getPosition() != null) {
                double distance = last.distance(end.getPosition());
                if (distance > GEOMETRY_ENDPOINT_TOLERANCE_METERS) {
                    violations.add(formatGeometryMismatch(
                        RoadNetworkViolationKind.EDGE_END_GEOMETRY_MISMATCH,
                        edge.getId(),
                        distance));
                }
            }
        }
    }

    private static String formatGeometryMismatch(
            RoadNetworkViolationKind kind,
            String edgeId,
            double distanceMeters) {
        return kind.name()
            + " edge " + edgeId
            + " distance " + distanceMeters
            + " tolerance " + GEOMETRY_ENDPOINT_TOLERANCE_METERS;
    }

    private static void validateSlopeOverrides(
            Map<String, RoadEdge> edges,
            List<String> violations) {
        for (RoadEdge edge : edges.values()) {
            double length = edge.getLength();
            for (RoadEdge.SlopeOverride override : edge.getSlopeOverrides()) {
                if (override.startDistance < 0.0) {
                    violations.add("edge " + edge.getId() + " slope override startDistance < 0");
                }
                if (override.endDistance < override.startDistance) {
                    violations.add("edge " + edge.getId() + " slope override endDistance < startDistance");
                }
                if (override.endDistance > length) {
                    violations.add("edge " + edge.getId() + " slope override endDistance > edge length "
                        + length);
                }
            }
        }
    }
}
