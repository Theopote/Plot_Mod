package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.model.Shape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.graph.RoadGraphEdits;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 道路网络拓扑构建（认领、求交打断、路口分类）
 */
public class RoadNetworkBuilder {
    public static final double NODE_TOLERANCE = 0.5;

    public enum JunctionType {
        ENDPOINT,
        THROUGH,
        T_JUNCTION,
        CROSSROAD,
        COMPLEX
    }

    public record AdoptResult(List<RoadEdge> edges, int junctionCount) {
        public AdoptResult {
            edges = List.copyOf(edges);
        }
    }

    public AdoptResult adoptShape(RoadNetwork network, Shape shape, RoadSystemConfig defaults) {
        List<Vec2d> points = RoadGeometryUtils.extractShapePoints(shape);
        if (points.size() < 2) {
            throw new IllegalArgumentException("Shape must have at least 2 points");
        }

        List<Vec2d> endpoints = shape.getEndpoints();
        Vec2d startPoint = endpoints != null && !endpoints.isEmpty()
            ? endpoints.getFirst()
            : points.getFirst();
        Vec2d endPoint = endpoints != null && endpoints.size() > 1
            ? endpoints.getLast()
            : points.getLast();

        RoadNode startNode = findOrCreateNode(network, startPoint);
        RoadNode endNode = findOrCreateNode(network, endPoint);

        Road road = network.createRoad(defaults);
        RoadEdge edge = network.createEdge(startNode.getId(), endNode.getId(), points, road.getId());

        Set<String> adoptedEdgeIds = new HashSet<>();
        adoptedEdgeIds.add(edge.getId());
        detectAndSplitIntersections(network, adoptedEdgeIds);

        List<RoadEdge> producedEdges = adoptedEdgeIds.stream()
            .map(network::getEdge)
            .filter(Objects::nonNull)
            .toList();
        if (producedEdges.isEmpty()) {
            throw new IllegalStateException("Adopted road produced no edges after intersection processing");
        }
        int junctionCount = Math.max(0, producedEdges.size() - 1);
        return new AdoptResult(producedEdges, junctionCount);
    }

    public void detectAndSplitIntersections(RoadNetwork network) {
        detectAndSplitIntersections(network, null);
    }

    public void detectAndSplitIntersections(RoadNetwork network, Set<String> trackedEdgeIds) {
        boolean changed = true;
        int safety = 0;
        while (changed && safety++ < 100) {
            changed = false;
            List<RoadEdge> edgeList = new ArrayList<>(network.getEdges().values());

            for (int i = 0; i < edgeList.size(); i++) {
                for (int j = i + 1; j < edgeList.size(); j++) {
                    RoadEdge edgeA = network.getEdge(edgeList.get(i).getId());
                    RoadEdge edgeB = network.getEdge(edgeList.get(j).getId());
                    if (edgeA == null || edgeB == null) {
                        continue;
                    }
                    if (edgeA.getRoadId() != null
                            && edgeA.getRoadId().equals(edgeB.getRoadId())) {
                        continue;
                    }

                    List<Vec2d> intersections = findIntersections(edgeA, edgeB);
                    for (Vec2d intersection : intersections) {
                        if (isNearEdgeEndpoint(edgeA, intersection) || isNearEdgeEndpoint(edgeB, intersection)) {
                            continue;
                        }
                        if (alreadyConnectedAt(network, edgeA, edgeB, intersection)) {
                            continue;
                        }

                        RoadNode junctionNode = findOrCreateNode(network, intersection);
                        boolean splitA = splitEdgeAtNode(network, edgeA.getId(), junctionNode.getId(), intersection, trackedEdgeIds);
                        boolean splitB = splitEdgeAtNode(network, edgeB.getId(), junctionNode.getId(), intersection, trackedEdgeIds);
                        if (splitA || splitB) {
                            changed = true;
                        }
                    }
                }
            }
        }
    }

    public JunctionType classify(RoadNode node) {
        if (node == null) {
            return JunctionType.ENDPOINT;
        }
        return switch (node.getDegree()) {
            case 0, 1 -> JunctionType.ENDPOINT;
            case 2 -> JunctionType.THROUGH;
            case 3 -> JunctionType.T_JUNCTION;
            case 4 -> JunctionType.CROSSROAD;
            default -> JunctionType.COMPLEX;
        };
    }

    private RoadNode findOrCreateNode(RoadNetwork network, Vec2d position) {
        for (RoadNode node : network.getNodes().values()) {
            if (RoadGeometryUtils.pointsNear(node.getPosition(), position, NODE_TOLERANCE)) {
                return node;
            }
        }
        return network.createNode(position);
    }

    private List<Vec2d> findIntersections(RoadEdge edgeA, RoadEdge edgeB) {
        PolylineShape polyA = new PolylineShape(edgeA.getCenterlinePoints(), false);
        PolylineShape polyB = new PolylineShape(edgeB.getCenterlinePoints(), false);
        List<Vec2d> raw = polyA.getIntersectionsWith(polyB);
        return deduplicatePoints(raw, NODE_TOLERANCE);
    }

    private List<Vec2d> deduplicatePoints(List<Vec2d> points, double tolerance) {
        List<Vec2d> unique = new ArrayList<>();
        for (Vec2d point : points) {
            boolean exists = false;
            for (Vec2d existing : unique) {
                if (RoadGeometryUtils.pointsNear(existing, point, tolerance)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                unique.add(point.copy());
            }
        }
        return unique;
    }

    private boolean isNearEdgeEndpoint(RoadEdge edge, Vec2d point) {
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.isEmpty()) {
            return true;
        }
        return RoadGeometryUtils.pointsNear(points.getFirst(), point, NODE_TOLERANCE)
            || RoadGeometryUtils.pointsNear(points.getLast(), point, NODE_TOLERANCE);
    }

    private boolean alreadyConnectedAt(RoadNetwork network, RoadEdge edgeA, RoadEdge edgeB, Vec2d point) {
        RoadNode sharedNode = findExistingNode(network, point);
        if (sharedNode == null) {
            return false;
        }
        String nodeId = sharedNode.getId();
        boolean aConnected = edgeA.getStartNodeId().equals(nodeId) || edgeA.getEndNodeId().equals(nodeId);
        boolean bConnected = edgeB.getStartNodeId().equals(nodeId) || edgeB.getEndNodeId().equals(nodeId);
        return aConnected && bConnected;
    }

    private RoadNode findExistingNode(RoadNetwork network, Vec2d position) {
        for (RoadNode node : network.getNodes().values()) {
            if (RoadGeometryUtils.pointsNear(node.getPosition(), position, NODE_TOLERANCE)) {
                return node;
            }
        }
        return null;
    }

    private boolean splitEdgeAtNode(
            RoadNetwork network,
            String edgeId,
            String nodeId,
            Vec2d splitPoint,
            Set<String> trackedEdgeIds) {
        boolean tracked = trackedEdgeIds != null && trackedEdgeIds.contains(edgeId);
        Optional<RoadGraphEdits.SplitResult> result = RoadGraphEdits.of(network)
            .splitEdgeAtNode(edgeId, nodeId, splitPoint, NODE_TOLERANCE);
        if (result.isEmpty()) {
            return false;
        }
        if (tracked) {
            trackedEdgeIds.remove(edgeId);
            trackedEdgeIds.add(result.get().firstEdgeId());
            trackedEdgeIds.add(result.get().secondEdgeId());
        }
        return true;
    }

    /** @deprecated 使用 {@link RoadGraphEdits#splitSlopeOverrides} */
    @Deprecated
    static List<RoadEdge.SlopeOverride> splitSlopeOverrides(
            List<RoadEdge.SlopeOverride> overrides,
            double splitDistance,
            double totalLength,
            boolean firstPart) {
        return RoadGraphEdits.splitSlopeOverrides(overrides, splitDistance, totalLength, firstPart);
    }
}
