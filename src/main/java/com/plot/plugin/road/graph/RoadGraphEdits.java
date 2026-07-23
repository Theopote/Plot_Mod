package com.plot.plugin.road.graph;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 道路网络拓扑编辑（打断、合并等）。
 */
public final class RoadGraphEdits {
    public static final double DEFAULT_NODE_TOLERANCE = RoadNetworkBuilder.NODE_TOLERANCE;

    private final RoadNetwork network;

    private RoadGraphEdits(RoadNetwork network) {
        this.network = network;
    }

    public static RoadGraphEdits of(RoadNetwork network) {
        return new RoadGraphEdits(network);
    }

    public Optional<SplitResult> splitEdgeAtNode(String edgeId, String nodeId) {
        RoadNode splitNode = network.getNode(nodeId);
        if (splitNode == null) {
            return Optional.empty();
        }
        return splitEdgeInternal(edgeId, nodeId, splitNode.getPosition(), DEFAULT_NODE_TOLERANCE);
    }

    public Optional<SplitResult> splitEdgeAtPoint(String edgeId, Vec2d splitPoint) {
        return splitEdgeAtPoint(edgeId, splitPoint, DEFAULT_NODE_TOLERANCE);
    }

    public Optional<SplitResult> splitEdgeAtPoint(String edgeId, Vec2d splitPoint, double tolerance) {
        if (splitPoint == null) {
            return Optional.empty();
        }
        RoadNode node = findOrCreateNode(splitPoint, tolerance);
        return splitEdgeInternal(edgeId, node.getId(), splitPoint, tolerance);
    }

    public Optional<String> mergeThroughNode(String nodeId) {
        RoadNode node = network.getNode(nodeId);
        if (node == null || node.getDegree() != 2) {
            return Optional.empty();
        }

        List<String> edgeIds = List.copyOf(node.getConnectedEdgeIds());
        if (edgeIds.size() != 2) {
            return Optional.empty();
        }

        RoadEdge edgeA = network.getEdge(edgeIds.get(0));
        RoadEdge edgeB = network.getEdge(edgeIds.get(1));
        if (edgeA == null || edgeB == null) {
            return Optional.empty();
        }

        String roadIdA = edgeA.getRoadId();
        String roadIdB = edgeB.getRoadId();
        if (roadIdA != null && roadIdB != null && !roadIdA.equals(roadIdB)) {
            return Optional.empty();
        }
        String roadId = roadIdA != null ? roadIdA : roadIdB;

        String nodeA = otherEndpoint(edgeA, nodeId);
        String nodeB = otherEndpoint(edgeB, nodeId);
        if (nodeA == null || nodeB == null) {
            return Optional.empty();
        }

        List<Vec2d> mergedPoints = mergeCenterlines(edgeA, edgeB, nodeA, nodeId, nodeB);
        if (mergedPoints.size() < 2) {
            return Optional.empty();
        }

        List<RoadEdge.SlopeOverride> mergedOverrides = mergeSlopeOverrides(edgeA, edgeB, nodeId);

        // 快照供失败回滚（detach 后不可仅 return empty）
        RoadEdge snapshotA = copyEdge(edgeA);
        RoadEdge snapshotB = copyEdge(edgeB);

        detachAndUnlink(edgeA);
        detachAndUnlink(edgeB);

        RoadNode through = network.getNode(nodeId);
        if (through == null || through.getDegree() != 0) {
            restoreEdge(snapshotA);
            restoreEdge(snapshotB);
            return Optional.empty();
        }

        try {
            network.removeNode(nodeId);
        } catch (IllegalStateException e) {
            restoreEdge(snapshotA);
            restoreEdge(snapshotB);
            return Optional.empty();
        }

        try {
            RoadEdge merged = network.createEdge(nodeA, nodeB, mergedPoints, roadId);
            merged.setSlopeOverrides(mergedOverrides);
            return Optional.of(merged.getId());
        } catch (RuntimeException e) {
            // 节点已删、合并失败：尽量恢复原边（through 节点无法完整恢复，记录错误）
            restoreEdge(snapshotA);
            restoreEdge(snapshotB);
            return Optional.empty();
        }
    }

    private static RoadEdge copyEdge(RoadEdge edge) {
        return new RoadEdge(
            edge.getId(),
            edge.getStartNodeId(),
            edge.getEndNodeId(),
            edge.getCenterlinePoints(),
            edge.getRoadId(),
            edge.getSlopeOverrides());
    }

    private void restoreEdge(RoadEdge snapshot) {
        if (snapshot == null) {
            return;
        }
        try {
            network.attachExistingEdge(snapshot);
        } catch (RuntimeException ignored) {
            // 回滚尽力而为
        }
    }

    public Optional<SplitResult> splitEdgeAtNode(String edgeId, String nodeId, Vec2d splitPoint, double tolerance) {
        return splitEdgeInternal(edgeId, nodeId, splitPoint, tolerance);
    }

    Optional<SplitResult> splitEdgeInternal(
            String edgeId,
            String nodeId,
            Vec2d splitPoint,
            double tolerance) {
        RoadEdge edge = network.getEdge(edgeId);
        RoadNode splitNode = network.getNode(nodeId);
        if (edge == null || splitNode == null) {
            return Optional.empty();
        }
        if (edge.getStartNodeId().equals(nodeId) || edge.getEndNodeId().equals(nodeId)) {
            return Optional.empty();
        }

        Vec2d point = splitPoint != null ? splitPoint : splitNode.getPosition();
        double effectiveTolerance = tolerance;

        List<List<Vec2d>> parts = RoadGeometryUtils.splitPolylineAt(
            edge.getCenterlinePoints(),
            point,
            effectiveTolerance
        );
        if (parts.size() != 2) {
            return Optional.empty();
        }

        List<Vec2d> firstPart = parts.get(0);
        List<Vec2d> secondPart = parts.get(1);
        if (firstPart.size() < 2 || secondPart.size() < 2) {
            return Optional.empty();
        }

        String startNodeId = edge.getStartNodeId();
        String endNodeId = edge.getEndNodeId();
        double splitDistance = RoadGeometryUtils.calculatePathLength(firstPart);
        double totalLength = edge.getLength();
        List<RoadEdge.SlopeOverride> slopeOverrides = edge.getSlopeOverrides();
        String roadId = edge.getRoadId();

        detachAndUnlink(edge);

        RoadEdge firstEdge = network.createEdge(startNodeId, nodeId, firstPart, roadId);
        RoadEdge secondEdge = network.createEdge(nodeId, endNodeId, secondPart, roadId);
        firstEdge.setSlopeOverrides(splitSlopeOverrides(slopeOverrides, splitDistance, totalLength, true));
        secondEdge.setSlopeOverrides(splitSlopeOverrides(slopeOverrides, splitDistance, totalLength, false));

        return Optional.of(new SplitResult(firstEdge.getId(), secondEdge.getId(), nodeId));
    }

    public static List<RoadEdge.SlopeOverride> splitSlopeOverrides(
            List<RoadEdge.SlopeOverride> overrides,
            double splitDistance,
            double totalLength,
            boolean firstPart) {
        if (overrides == null || overrides.isEmpty()) {
            return List.of();
        }

        List<RoadEdge.SlopeOverride> result = new ArrayList<>();
        for (RoadEdge.SlopeOverride override : overrides) {
            double start = override.startDistance;
            double end = override.endDistance;
            if (firstPart) {
                if (end <= 0 || start >= splitDistance) {
                    continue;
                }
                double newStart = Math.max(0, start);
                double newEnd = Math.min(splitDistance, end);
                if (newEnd > newStart) {
                    result.add(new RoadEdge.SlopeOverride(newStart, newEnd, override.maxSlope));
                }
            } else {
                double secondLength = Math.max(0, totalLength - splitDistance);
                if (secondLength <= 0 || end <= splitDistance || start >= totalLength) {
                    continue;
                }
                double newStart = Math.max(splitDistance, start) - splitDistance;
                double newEnd = Math.min(totalLength, end) - splitDistance;
                if (newEnd > newStart) {
                    result.add(new RoadEdge.SlopeOverride(newStart, newEnd, override.maxSlope));
                }
            }
        }
        return result;
    }

    private RoadNode findOrCreateNode(Vec2d position, double tolerance) {
        for (RoadNode node : network.getNodes().values()) {
            if (RoadGeometryUtils.pointsNear(node.getPosition(), position, tolerance)) {
                return node;
            }
        }
        return network.createNode(position);
    }

    private void detachAndUnlink(RoadEdge edge) {
        String roadId = edge.getRoadId();
        network.detachEdge(edge.getId());
        if (roadId != null) {
            Road road = network.getRoad(roadId);
            if (road != null) {
                road.removeSegment(edge.getId());
            }
        }
    }

    private static String otherEndpoint(RoadEdge edge, String nodeId) {
        if (edge.getStartNodeId().equals(nodeId)) {
            return edge.getEndNodeId();
        }
        if (edge.getEndNodeId().equals(nodeId)) {
            return edge.getStartNodeId();
        }
        return null;
    }

    private static List<Vec2d> mergeCenterlines(
            RoadEdge edgeA,
            RoadEdge edgeB,
            String nodeA,
            String throughNode,
            String nodeB) {
        List<Vec2d> partA = orientCenterline(edgeA, nodeA, throughNode);
        List<Vec2d> partB = orientCenterline(edgeB, throughNode, nodeB);
        if (partA.isEmpty() || partB.isEmpty()) {
            return List.of();
        }

        List<Vec2d> merged = new ArrayList<>(partA);
        if (merged.getLast().distance(partB.getFirst()) <= DEFAULT_NODE_TOLERANCE) {
            for (int i = 1; i < partB.size(); i++) {
                merged.add(partB.get(i).copy());
            }
        } else {
            merged.addAll(partB.stream().map(Vec2d::copy).toList());
        }
        return merged;
    }

    private static List<Vec2d> orientCenterline(RoadEdge edge, String fromNodeId, String toNodeId) {
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.isEmpty()) {
            return List.of();
        }
        if (edge.getStartNodeId().equals(fromNodeId) && edge.getEndNodeId().equals(toNodeId)) {
            return points.stream().map(Vec2d::copy).toList();
        }
        if (edge.getStartNodeId().equals(toNodeId) && edge.getEndNodeId().equals(fromNodeId)) {
            List<Vec2d> reversed = new ArrayList<>(points.size());
            for (int i = points.size() - 1; i >= 0; i--) {
                reversed.add(points.get(i).copy());
            }
            return reversed;
        }
        return List.of();
    }

    private static List<RoadEdge.SlopeOverride> mergeSlopeOverrides(
            RoadEdge edgeA,
            RoadEdge edgeB,
            String throughNodeId) {
        String nodeA = otherEndpoint(edgeA, throughNodeId);
        String nodeB = otherEndpoint(edgeB, throughNodeId);
        if (nodeA == null || nodeB == null) {
            return List.of();
        }

        List<RoadEdge.SlopeOverride> first = orientOverrides(edgeA, nodeA, throughNodeId);
        List<RoadEdge.SlopeOverride> second = orientOverrides(edgeB, throughNodeId, nodeB);
        double offset = edgeA.getLength();

        List<RoadEdge.SlopeOverride> merged = new ArrayList<>(first);
        for (RoadEdge.SlopeOverride override : second) {
            merged.add(new RoadEdge.SlopeOverride(
                override.startDistance + offset,
                override.endDistance + offset,
                override.maxSlope
            ));
        }
        return merged;
    }

    private static List<RoadEdge.SlopeOverride> orientOverrides(
            RoadEdge edge,
            String fromNodeId,
            String toNodeId) {
        if (edge.getStartNodeId().equals(fromNodeId) && edge.getEndNodeId().equals(toNodeId)) {
            return edge.getSlopeOverrides();
        }
        if (edge.getStartNodeId().equals(toNodeId) && edge.getEndNodeId().equals(fromNodeId)) {
            double length = edge.getLength();
            List<RoadEdge.SlopeOverride> reversed = new ArrayList<>();
            for (RoadEdge.SlopeOverride override : edge.getSlopeOverrides()) {
                reversed.add(new RoadEdge.SlopeOverride(
                    length - override.endDistance,
                    length - override.startDistance,
                    override.maxSlope
                ));
            }
            return reversed;
        }
        return List.of();
    }

    public record SplitResult(String firstEdgeId, String secondEdgeId, String nodeId) {
    }
}
