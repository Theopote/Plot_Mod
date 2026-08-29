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
import com.plot.plugin.road.model.RoadSegmentOrdering;
import com.plot.plugin.road.spatial.RoadEdgeSpatialIndex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadNetworkBuilder");

    public static final double NODE_TOLERANCE = 0.5;
    public static final int MAX_INTERSECTION_PASSES = 100;
    /** 求交探测中边数超过此阈值则中止，避免平行重复边导致组合爆炸 OOM。 */
    static final int MAX_INTERSECTION_EDGE_COUNT = 2_000;

    public enum JunctionType {
        ENDPOINT,
        THROUGH,
        T_JUNCTION,
        CROSSROAD,
        COMPLEX
    }

    public record AdoptResult(
            List<RoadEdge> edges,
            int junctionCount,
            IntersectionResult intersectionResult) {
        public AdoptResult {
            edges = List.copyOf(edges);
            if (intersectionResult == null) {
                intersectionResult = IntersectionResult.COMPLETE;
            }
        }
    }

    public AdoptResult adoptShape(RoadNetwork network, Shape shape, RoadSystemConfig defaults) {
        List<Vec2d> points = RoadGeometryUtils.extractShapePoints(shape);
        if (points.size() < 2) {
            throw new IllegalArgumentException("Shape must have at least 2 points");
        }

        // 端点优先用离散中心线首尾（闭合圆/矩形等 getEndpoints 可能为空，首尾重合则共用一个节点）
        Vec2d startPoint = points.getFirst();
        Vec2d endPoint = points.getLast();
        List<Vec2d> shapeEndpoints = shape.getEndpoints();
        if (shapeEndpoints != null && shapeEndpoints.size() >= 2
            && !RoadGeometryUtils.pointsNear(startPoint, endPoint, NODE_TOLERANCE)) {
            // 开放路径：若 Shape 声明了端点且与中心线端点一致量级，沿用声明端点便于吸附
            startPoint = shapeEndpoints.getFirst();
            endPoint = shapeEndpoints.getLast();
        }

        RoadNode startNode = findOrCreateNode(network, startPoint);
        RoadNode endNode = findOrCreateNode(network, endPoint);

        Road road = network.createRoad(defaults);
        RoadEdge edge = network.createEdge(startNode.getId(), endNode.getId(), points, road.getId());
        edge.setSourceRoadId(UUID.randomUUID().toString());

        Set<String> adoptedEdgeIds = new HashSet<>();
        adoptedEdgeIds.add(edge.getId());
        IntersectionResult intersectionResult =
            detectAndSplitIntersections(network, adoptedEdgeIds);

        List<RoadEdge> producedEdges = adoptedEdgeIds.stream()
            .map(network::getEdge)
            .filter(Objects::nonNull)
            .toList();
        if (producedEdges.isEmpty()) {
            throw new IllegalStateException("Adopted road produced no edges after intersection processing");
        }
        int junctionCount = Math.max(0, producedEdges.size() - 1);
        return new AdoptResult(producedEdges, junctionCount, intersectionResult);
    }

    public IntersectionResult detectAndSplitIntersections(RoadNetwork network) {
        return detectAndSplitIntersections(network, null);
    }

    public IntersectionResult detectAndSplitIntersections(RoadNetwork network, Set<String> trackedEdgeIds) {
        return detectAndSplitIntersections(network, trackedEdgeIds, MAX_INTERSECTION_PASSES);
    }

    IntersectionResult detectAndSplitIntersections(
            RoadNetwork network,
            Set<String> trackedEdgeIds,
            int maxPasses) {
        boolean changed = true;
        int pass = 0;
        while (changed && pass < maxPasses) {
            if (network.getEdges().size() > MAX_INTERSECTION_EDGE_COUNT) {
                LOGGER.error(
                    "Intersection splitting aborted: edge count {} exceeds limit {}",
                    network.getEdges().size(),
                    MAX_INTERSECTION_EDGE_COUNT);
                RoadSegmentOrdering.applyTopologicalOrderToAllRoads(network);
                return IntersectionResult.INCOMPLETE;
            }
            pass++;
            changed = false;
            RoadEdgeSpatialIndex spatialIndex = RoadEdgeSpatialIndex.build(
                network.getEdges().values(), NODE_TOLERANCE);

            for (RoadEdgeSpatialIndex.CandidatePair pair : spatialIndex.candidatePairs()) {
                RoadEdge edgeA = network.getEdge(pair.edgeIdA());
                RoadEdge edgeB = network.getEdge(pair.edgeIdB());
                if (edgeA == null || edgeB == null) {
                    continue;
                }
                if (shouldSkipEdgePair(edgeA, edgeB)) {
                    continue;
                }

                List<Vec2d> connectionPoints = findConnectionPoints(edgeA, edgeB);
                for (Vec2d connectionPoint : connectionPoints) {
                    if (alreadyConnectedAt(network, edgeA, edgeB, connectionPoint)) {
                        continue;
                    }
                    if (processIntersection(
                            network,
                            edgeA,
                            edgeB,
                            connectionPoint,
                            trackedEdgeIds)) {
                        changed = true;
                    }
                }
            }
        }
        RoadSegmentOrdering.applyTopologicalOrderToAllRoads(network);
        if (changed) {
            LOGGER.error(
                "Intersection splitting stopped after {} passes (edge count={}); topology may be incomplete",
                maxPasses,
                network.getEdges().size());
            return IntersectionResult.INCOMPLETE;
        }
        return IntersectionResult.COMPLETE;
    }

    /**
     * Probes whether running intersection splitting would still change topology, without mutating
     * the live network.
     */
    public IntersectionProbeResult probeIntersectionCompleteness(RoadNetwork network) {
        if (network == null || network.getEdges().isEmpty()) {
            return IntersectionProbeResult.resolved();
        }
        RoadNetwork probe = network.snapshot();
        int edgesAtStart = probe.getEdges().size();
        int nodesAtStart = probe.getNodes().size();
        IntersectionResult result = detectAndSplitIntersections(probe);
        boolean topologyWouldChange = probe.getEdges().size() != edgesAtStart
            || probe.getNodes().size() != nodesAtStart;
        return new IntersectionProbeResult(result, topologyWouldChange);
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

    private static boolean shouldSkipEdgePair(RoadEdge edgeA, RoadEdge edgeB) {
        if (edgeA.getSourceRoadId() != null
                && edgeA.getSourceRoadId().equals(edgeB.getSourceRoadId())) {
            return true;
        }
        if (edgeA.getRoadId() != null && edgeA.getRoadId().equals(edgeB.getRoadId())) {
            return true;
        }
        // 同一对节点间的平行重复边（不同 roadId）无需互相求交，否则会沿重合线段无限切分。
        return sharesSameEndpointPair(edgeA, edgeB);
    }

    private static boolean sharesSameEndpointPair(RoadEdge edgeA, RoadEdge edgeB) {
        String aStart = edgeA.getStartNodeId();
        String aEnd = edgeA.getEndNodeId();
        String bStart = edgeB.getStartNodeId();
        String bEnd = edgeB.getEndNodeId();
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) {
            return false;
        }
        return (aStart.equals(bStart) && aEnd.equals(bEnd))
            || (aStart.equals(bEnd) && aEnd.equals(bStart));
    }

    private List<Vec2d> findConnectionPoints(RoadEdge edgeA, RoadEdge edgeB) {
        List<Vec2d> points = new ArrayList<>();
        points.addAll(findIntersections(edgeA, edgeB));
        points.addAll(findEndpointApproaches(edgeA, edgeB));
        points.addAll(findEndpointApproaches(edgeB, edgeA));
        points.addAll(findNearbyEndpointPairs(edgeA, edgeB));
        return deduplicatePoints(points, NODE_TOLERANCE);
    }

    /** 一条边的端点落在另一条边 interior 上（含容差吸附）。 */
    private List<Vec2d> findEndpointApproaches(RoadEdge endpointEdge, RoadEdge targetEdge) {
        List<Vec2d> approaches = new ArrayList<>();
        List<Vec2d> targetPoints = targetEdge.getCenterlinePoints();
        if (targetPoints.size() < 2) {
            return approaches;
        }
        for (Vec2d endpoint : edgeEndpointPoints(endpointEdge)) {
            Vec2d projected = projectOntoPolylineInterior(targetPoints, endpoint, NODE_TOLERANCE);
            if (projected != null) {
                approaches.add(projected);
            }
        }
        return approaches;
    }

    /** 两条边端点彼此落在容差范围内。 */
    private List<Vec2d> findNearbyEndpointPairs(RoadEdge edgeA, RoadEdge edgeB) {
        List<Vec2d> pairs = new ArrayList<>();
        for (Vec2d epA : edgeEndpointPoints(edgeA)) {
            for (Vec2d epB : edgeEndpointPoints(edgeB)) {
                if (RoadGeometryUtils.pointsNear(epA, epB, NODE_TOLERANCE)) {
                    pairs.add(epA.copy());
                }
            }
        }
        return pairs;
    }

    private List<Vec2d> edgeEndpointPoints(RoadEdge edge) {
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.isEmpty()) {
            return List.of();
        }
        if (points.size() == 1) {
            return List.of(points.getFirst().copy());
        }
        return List.of(points.getFirst().copy(), points.getLast().copy());
    }

    private Vec2d projectOntoPolylineInterior(List<Vec2d> polyline, Vec2d point, double tolerance) {
        Vec2d bestProjection = null;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < polyline.size() - 1; i++) {
            Vec2d projected = RoadGeometryUtils.projectPointOnSegment(
                polyline.get(i), polyline.get(i + 1), point);
            double distance = projected.distance(point);
            if (distance > tolerance || distance >= bestDistance) {
                continue;
            }
            if (RoadGeometryUtils.pointsNear(projected, polyline.getFirst(), tolerance)
                    || RoadGeometryUtils.pointsNear(projected, polyline.getLast(), tolerance)) {
                continue;
            }
            bestDistance = distance;
            bestProjection = projected;
        }
        return bestProjection != null ? bestProjection.copy() : null;
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

    /**
     * 按边分别处理交点：仅对 interior 边 split；endpoint 边复用/合并节点。
     */
    private boolean processIntersection(
            RoadNetwork network,
            RoadEdge edgeA,
            RoadEdge edgeB,
            Vec2d intersection,
            Set<String> trackedEdgeIds) {
        // 边可能在上一轮 split 后已被替换
        RoadEdge currentA = network.getEdge(edgeA.getId());
        RoadEdge currentB = network.getEdge(edgeB.getId());
        if (currentA == null || currentB == null) {
            return false;
        }

        boolean aAtEndpoint = isNearEdgeEndpoint(currentA, intersection);
        boolean bAtEndpoint = isNearEdgeEndpoint(currentB, intersection);
        RoadNode junctionNode = findOrCreateNode(network, intersection);

        boolean changed = false;
        if (aAtEndpoint) {
            changed |= connectEdgeEndpointToNode(network, currentA, intersection, junctionNode);
        } else {
            changed |= splitEdgeAtNode(
                network, currentA.getId(), junctionNode.getId(), intersection, trackedEdgeIds);
        }
        if (bAtEndpoint) {
            changed |= connectEdgeEndpointToNode(network, currentB, intersection, junctionNode);
        } else {
            changed |= splitEdgeAtNode(
                network, currentB.getId(), junctionNode.getId(), intersection, trackedEdgeIds);
        }
        return changed;
    }

    private boolean connectEdgeEndpointToNode(
            RoadNetwork network,
            RoadEdge edge,
            Vec2d intersection,
            RoadNode junctionNode) {
        String endpointNodeId = findEndpointNodeId(edge, intersection);
        if (endpointNodeId == null || endpointNodeId.equals(junctionNode.getId())) {
            return false;
        }
        return mergeEdgeEndpointToNode(network, edge, endpointNodeId, junctionNode.getId());
    }

    private String findEndpointNodeId(RoadEdge edge, Vec2d intersection) {
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

    private boolean mergeEdgeEndpointToNode(
            RoadNetwork network,
            RoadEdge edge,
            String oldNodeId,
            String newNodeId) {
        if (oldNodeId.equals(newNodeId)) {
            return false;
        }
        RoadNode oldNode = network.getNode(oldNodeId);
        RoadNode newNode = network.getNode(newNodeId);
        if (oldNode == null || newNode == null) {
            return false;
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
        return relinked;
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
}
