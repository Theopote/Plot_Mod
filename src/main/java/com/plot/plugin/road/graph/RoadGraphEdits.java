package com.plot.plugin.road.graph;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.centerline.CenterlinePhase2ConsistencyPolicy;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetworkInvariantValidator;
import com.plot.plugin.road.station.RoadStationDataTransforms.SegmentGeometrySnapshot;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 道路网络拓扑编辑（打断、合并等）。
 */
public final class RoadGraphEdits {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadGraph");
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

    /**
     * Geometric split at a plane point: projects onto the edge centerline first, then creates
     * an independent node at the projection. Does not warp the centerline to an off-line pick.
     */
    public Optional<SplitResult> splitEdgeAtPoint(String edgeId, Vec2d splitPoint, double tolerance) {
        if (splitPoint == null) {
            return Optional.empty();
        }
        RoadEdge edge = network.getEdge(edgeId);
        if (edge == null) {
            return Optional.empty();
        }
        double pickTol = Double.isFinite(tolerance) && tolerance > 0 ? tolerance : DEFAULT_NODE_TOLERANCE;
        List<Vec2d> points = edge.getCenterlinePoints();
        int nearest = RoadGeometryUtils.findNearestSegmentIndex(points, splitPoint);
        if (nearest < 0) {
            return Optional.empty();
        }
        Vec2d projected = RoadGeometryUtils.projectPointOnSegment(
            points.get(nearest), points.get(nearest + 1), splitPoint);
        if (projected.distance(splitPoint) > pickTol) {
            return Optional.empty();
        }
        RoadNode node = network.createNode(projected.copy());
        Optional<SplitResult> split = splitEdgeInternal(edgeId, node.getId(), projected, pickTol);
        if (split.isEmpty() && network.getNode(node.getId()) != null
                && network.getNode(node.getId()).getDegree() == 0) {
            network.removeNode(node.getId());
        }
        return split;
    }

    /**
     * Split using a known local arc distance (segment index), never nearest-segment search.
     * Always creates an independent node at the exact split point.
     */
    public Optional<SplitResult> splitEdgeAtLocalDistance(String edgeId, double localDistance) {
        RoadEdge edge = network.getEdge(edgeId);
        if (edge == null || !Double.isFinite(localDistance)) {
            return Optional.empty();
        }
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.size() < 2) {
            return Optional.empty();
        }
        double total = RoadGeometryUtils.calculatePathLength(points);
        if (localDistance <= DEFAULT_NODE_TOLERANCE || localDistance >= total - DEFAULT_NODE_TOLERANCE) {
            return Optional.empty();
        }
        int segmentIndex = segmentIndexAtLocalDistance(points, localDistance);
        if (segmentIndex < 0) {
            return Optional.empty();
        }
        Vec2d splitPoint = RoadGeometryUtils.pointAtDistance(points, localDistance);
        if (splitPoint == null) {
            return Optional.empty();
        }
        RoadNode node = network.createNode(splitPoint.copy());
        Optional<SplitResult> split = splitEdgeAtInteriorSegment(
            edgeId, node.getId(), segmentIndex, splitPoint, DEFAULT_NODE_TOLERANCE);
        if (split.isEmpty() && network.getNode(node.getId()) != null
                && network.getNode(node.getId()).getDegree() == 0) {
            network.removeNode(node.getId());
        }
        return split;
    }

    public Optional<MergeResult> mergeThroughNode(String nodeId) {
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
        String sourceRoadId = edgeA.getSourceRoadId() != null ? edgeA.getSourceRoadId() : edgeB.getSourceRoadId();

        String nodeA = otherEndpoint(edgeA, nodeId);
        String nodeB = otherEndpoint(edgeB, nodeId);
        if (nodeA == null || nodeB == null) {
            return Optional.empty();
        }
        // Reject unintended self-loop merge.
        if (nodeA.equals(nodeB)) {
            return Optional.empty();
        }

        List<Vec2d> mergedPoints = mergeCenterlines(edgeA, edgeB, nodeA, nodeId, nodeB);
        if (mergedPoints.size() < 2) {
            return Optional.empty();
        }

        // Lineage: first old edge spans nodeA→through; second spans through→nodeB.
        RoadEdge firstOld = edgeA.getStartNodeId().equals(nodeA) || edgeA.getEndNodeId().equals(nodeA)
            ? edgeA : edgeB;
        RoadEdge secondOld = firstOld == edgeA ? edgeB : edgeA;
        if (!(firstOld.getStartNodeId().equals(nodeA) || firstOld.getEndNodeId().equals(nodeA))) {
            firstOld = edgeA;
            secondOld = edgeB;
        }
        double firstOldLength = firstOld.getLength();
        double secondOldLength = secondOld.getLength();
        double secondLocalOffset = firstOldLength;
        // Storage reversed relative to merged polyline direction nodeA→through→nodeB.
        boolean firstReversed = firstOld.getStartNodeId().equals(nodeId)
            && firstOld.getEndNodeId().equals(nodeA);
        boolean secondReversed = secondOld.getStartNodeId().equals(nodeB)
            && secondOld.getEndNodeId().equals(nodeId);
        String firstOldId = firstOld.getId();
        String secondOldId = secondOld.getId();

        List<RoadEdge.SlopeOverride> mergedOverrides = mergeSlopeOverrides(edgeA, edgeB, nodeId);

        // Snapshot edges before unlink; keep through-node until createEdge succeeds.
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

        RoadEdge merged = null;
        try {
            merged = network.createEdge(nodeA, nodeB, mergedPoints, roadId);
            merged.setSlopeOverrides(mergedOverrides);
            if (sourceRoadId != null) {
                merged.setSourceRoadId(sourceRoadId);
            }
            if (through.getDegree() == 0) {
                network.removeNode(nodeId);
            }
            return Optional.of(new MergeResult(
                merged.getId(),
                firstOldId,
                secondOldId,
                secondLocalOffset,
                firstOldLength,
                secondOldLength,
                firstReversed,
                secondReversed));
        } catch (RuntimeException e) {
            if (merged != null && network.getEdge(merged.getId()) != null) {
                network.unassignEdgeFromRoad(merged.getId());
                network.detachEdge(merged.getId());
            }
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
            edge.getSlopeOverrides(),
            edge.getSourceRoadId());
    }

    private void restoreEdge(RoadEdge snapshot) {
        if (snapshot == null) {
            return;
        }
        try {
            network.attachExistingEdge(snapshot);
        } catch (RuntimeException e) {
            LOGGER.debug("边回滚 attach 失败（尽力而为）: {} ({})", snapshot.getId(), e.toString());
        }
    }

    public Optional<SplitResult> splitEdgeAtNode(String edgeId, String nodeId, Vec2d splitPoint, double tolerance) {
        return splitEdgeInternal(edgeId, nodeId, splitPoint, tolerance);
    }

    /**
     * 在指定线段上分割边，允许分割节点已是边的端点（用于自交叠点二次穿越）。
     */
    public Optional<SplitResult> splitEdgeAtInteriorSegment(
            String edgeId,
            String nodeId,
            int segmentIndex,
            Vec2d splitPoint,
            double tolerance) {
        RoadEdge edge = network.getEdge(edgeId);
        RoadNode splitNode = network.getNode(nodeId);
        if (edge == null || splitNode == null || splitPoint == null) {
            return Optional.empty();
        }
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.size() < 2 || segmentIndex < 0 || segmentIndex >= points.size() - 1) {
            return Optional.empty();
        }
        double pickTol = Double.isFinite(tolerance) && tolerance > 0 ? tolerance : DEFAULT_NODE_TOLERANCE;
        Vec2d segStart = points.get(segmentIndex);
        Vec2d segEnd = points.get(segmentIndex + 1);
        Vec2d projected = RoadGeometryUtils.projectPointOnSegment(segStart, segEnd, splitPoint);
        // Split point must lie on the named segment; node must lie on that projection.
        if (projected.distance(splitPoint) > pickTol) {
            return Optional.empty();
        }
        if (splitNode.getPosition().distance(projected) > pickTol) {
            return Optional.empty();
        }
        // Ordinary geometric split: cut at the on-centerline projection, not an off-line pick.
        List<List<Vec2d>> parts = RoadGeometryUtils.splitPolylineAtSegment(
            points,
            segmentIndex,
            projected);
        if (parts.size() != 2) {
            return Optional.empty();
        }
        // Parts end on the centerline projection; snap to the validated nearby node (pickTol).
        // Do not use the 1mm invariant tolerance here — station/registry picks can sit slightly
        // off the geometric mid-point (skew crossings) while still within node pickTol.
        Vec2d nodePos = splitNode.getPosition().copy();
        if (!snapPartEndpointWithinTolerance(parts.get(0), false, nodePos, pickTol)
                || !snapPartEndpointWithinTolerance(parts.get(1), true, nodePos, pickTol)) {
            return Optional.empty();
        }
        return finalizeSplit(edge, nodeId, parts.get(0), parts.get(1));
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
        double pickTol = Double.isFinite(tolerance) && tolerance > 0 ? tolerance : DEFAULT_NODE_TOLERANCE;
        List<Vec2d> points = edge.getCenterlinePoints();
        int nearest = RoadGeometryUtils.findNearestSegmentIndex(points, point);
        if (nearest < 0) {
            return Optional.empty();
        }
        Vec2d projected = RoadGeometryUtils.projectPointOnSegment(
            points.get(nearest), points.get(nearest + 1), point);
        if (projected.distance(point) > pickTol
                || splitNode.getPosition().distance(projected) > pickTol) {
            return Optional.empty();
        }

        List<List<Vec2d>> parts = RoadGeometryUtils.splitPolylineAt(
            points,
            projected,
            pickTol
        );
        if (parts.size() != 2) {
            return Optional.empty();
        }
        Vec2d nodePos = splitNode.getPosition().copy();
        if (!snapPartEndpointWithinTolerance(parts.get(0), false, nodePos, pickTol)
                || !snapPartEndpointWithinTolerance(parts.get(1), true, nodePos, pickTol)) {
            return Optional.empty();
        }
        return finalizeSplit(edge, nodeId, parts.get(0), parts.get(1));
    }

    private Optional<SplitResult> finalizeSplit(
            RoadEdge edge,
            String nodeId,
            List<Vec2d> firstPart,
            List<Vec2d> secondPart) {
        if (firstPart.size() < 2 || secondPart.size() < 2) {
            return Optional.empty();
        }
        if (!allFinite(firstPart) || !allFinite(secondPart)) {
            return Optional.empty();
        }
        RoadNode splitNode = network.getNode(nodeId);
        if (splitNode == null) {
            return Optional.empty();
        }
        // Caller already snapped within pickTol; re-assert endpoints match the topology node.
        Vec2d nodePos = splitNode.getPosition().copy();
        if (!snapPartEndpointWithinTolerance(firstPart, false, nodePos, DEFAULT_NODE_TOLERANCE)
                || !snapPartEndpointWithinTolerance(secondPart, true, nodePos, DEFAULT_NODE_TOLERANCE)) {
            return Optional.empty();
        }
        double minPartLength = RoadNetworkInvariantValidator.GEOMETRY_ENDPOINT_TOLERANCE_METERS;
        if (RoadGeometryUtils.calculatePathLength(firstPart) < minPartLength
            || RoadGeometryUtils.calculatePathLength(secondPart) < minPartLength) {
            return Optional.empty();
        }

        String startNodeId = edge.getStartNodeId();
        String endNodeId = edge.getEndNodeId();
        String oldEdgeId = edge.getId();
        double splitDistance = RoadGeometryUtils.calculatePathLength(firstPart);
        double totalLength = edge.getLength();
        List<RoadEdge.SlopeOverride> slopeOverrides = edge.getSlopeOverrides();
        List<RoadEdge.SlopeOverride> firstSlopes =
            splitSlopeOverrides(slopeOverrides, splitDistance, totalLength, true);
        List<RoadEdge.SlopeOverride> secondSlopes =
            splitSlopeOverrides(slopeOverrides, splitDistance, totalLength, false);
        if (!slopeOverridesValid(firstSlopes, splitDistance)
                || !slopeOverridesValid(secondSlopes, RoadGeometryUtils.calculatePathLength(secondPart))) {
            return Optional.empty();
        }
        String roadId = edge.getRoadId();
        String sourceRoadId = edge.getSourceRoadId();

        RoadEdge snapshot = copyEdge(edge);
        detachAndUnlink(edge);

        RoadEdge firstEdge = null;
        RoadEdge secondEdge = null;
        try {
            firstEdge = network.createEdge(startNodeId, nodeId, firstPart, roadId);
            secondEdge = network.createEdge(nodeId, endNodeId, secondPart, roadId);
            firstEdge.setSourceRoadId(sourceRoadId);
            secondEdge.setSourceRoadId(sourceRoadId);
            firstEdge.setSlopeOverrides(firstSlopes);
            secondEdge.setSlopeOverrides(secondSlopes);
            return Optional.of(new SplitResult(
                firstEdge.getId(), secondEdge.getId(), nodeId, oldEdgeId, splitDistance));
        } catch (RuntimeException e) {
            cleanupCreatedChild(secondEdge);
            cleanupCreatedChild(firstEdge);
            restoreEdge(snapshot);
            return Optional.empty();
        }
    }

    private static boolean snapPartEndpointWithinTolerance(
            List<Vec2d> part,
            boolean atStart,
            Vec2d nodePos,
            double tolerance) {
        if (part == null || part.size() < 2 || nodePos == null) {
            return false;
        }
        int index = atStart ? 0 : part.size() - 1;
        Vec2d current = part.get(index);
        if (current.distance(nodePos) > tolerance) {
            return false;
        }
        part.set(index, nodePos.copy());
        return true;
    }

    private static boolean allFinite(List<Vec2d> points) {
        for (Vec2d point : points) {
            if (point == null || !Double.isFinite(point.x) || !Double.isFinite(point.y)) {
                return false;
            }
        }
        return true;
    }

    private static boolean slopeOverridesValid(List<RoadEdge.SlopeOverride> overrides, double edgeLength) {
        if (overrides == null || overrides.isEmpty()) {
            return true;
        }
        if (!Double.isFinite(edgeLength) || edgeLength < 0) {
            return false;
        }
        for (RoadEdge.SlopeOverride override : overrides) {
            if (override == null
                    || !Double.isFinite(override.startDistance)
                    || !Double.isFinite(override.endDistance)
                    || override.startDistance < -1e-6
                    || override.endDistance > edgeLength + 1e-6
                    || override.startDistance > override.endDistance + 1e-6) {
                return false;
            }
        }
        return true;
    }

    private void cleanupCreatedChild(RoadEdge child) {
        if (child == null || network.getEdge(child.getId()) == null) {
            return;
        }
        network.unassignEdgeFromRoad(child.getId());
        network.detachEdge(child.getId());
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

    private static int segmentIndexAtLocalDistance(List<Vec2d> points, double localDistance) {
        if (points == null || points.size() < 2 || !Double.isFinite(localDistance)) {
            return -1;
        }
        double target = Math.max(0.0, localDistance);
        double accumulated = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            double segmentLength = points.get(i).distance(points.get(i + 1));
            if (segmentLength <= 1e-9) {
                continue;
            }
            if (accumulated + segmentLength >= target - 1e-9) {
                boolean atPolyStart = i == 0 && target <= 1e-9;
                boolean atPolyEnd = i == points.size() - 2
                    && target >= accumulated + segmentLength - 1e-9;
                if (atPolyStart || atPolyEnd) {
                    return -1;
                }
                return i;
            }
            accumulated += segmentLength;
        }
        return -1;
    }

    private void detachAndUnlink(RoadEdge edge) {
        network.unassignEdgeFromRoad(edge.getId());
        network.detachEdge(edge.getId());
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

    /**
     * 将两个交叉点合并为一个：保留 {@code survivorId}，把 {@code absorbedId} 上的边改接到 survivor。
     * 若会产生同 road 的平行重复边则拒绝合并。
     */
    public Optional<String> mergeJunctionNode(String survivorId, String absorbedId) {
        if (survivorId == null || absorbedId == null || survivorId.equals(absorbedId)) {
            return Optional.empty();
        }
        RoadNode survivor = network.getNode(survivorId);
        RoadNode absorbed = network.getNode(absorbedId);
        if (survivor == null || absorbed == null) {
            return Optional.empty();
        }

        List<String> edgeIds = List.copyOf(absorbed.getConnectedEdgeIds());
        for (String edgeId : edgeIds) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            String neighborId = otherEndpoint(edge, absorbedId);
            if (neighborId == null) {
                continue;
            }
            if (hasParallelEdge(survivorId, neighborId, edge.getRoadId(), edgeId)) {
                return Optional.empty();
            }
        }

        // All-or-nothing: every connected edge must be able to relink, or reject before mutating.
        List<String> toRelink = new ArrayList<>();
        for (String edgeId : List.copyOf(absorbed.getConnectedEdgeIds())) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            if (!canRelinkEdgeEndpoint(edge, absorbedId, survivorId)) {
                return Optional.empty();
            }
            toRelink.add(edgeId);
        }
        if (toRelink.isEmpty()) {
            return Optional.empty();
        }

        List<CenterlinePhase2ConsistencyPolicy.JunctionMergeEdgeEdit> relinks = new ArrayList<>();
        List<RoadEdge> snapshots = new ArrayList<>();
        for (String edgeId : toRelink) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                rollbackJunctionMerge(relinks, snapshots, absorbedId);
                return Optional.empty();
            }
            snapshots.add(copyEdge(edge));
            SegmentGeometrySnapshot before = SegmentGeometrySnapshot.capture(network, edgeId);
            if (!relinkEdgeEndpoint(edge, absorbedId, survivorId)) {
                rollbackJunctionMerge(relinks, snapshots, absorbedId);
                return Optional.empty();
            }
            relinks.add(new CenterlinePhase2ConsistencyPolicy.JunctionMergeEdgeEdit(edgeId, before));
        }
        if (absorbed.getDegree() == 0) {
            network.removeNode(absorbedId);
        }
        CenterlinePhase2ConsistencyPolicy.afterJunctionNodeMerge(network, relinks);
        return Optional.of(survivorId);
    }

    private void rollbackJunctionMerge(
            List<CenterlinePhase2ConsistencyPolicy.JunctionMergeEdgeEdit> succeeded,
            List<RoadEdge> snapshots,
            String absorbedId) {
        // Best-effort: restore snapshotted edges that were already mutated.
        for (RoadEdge snapshot : snapshots) {
            RoadEdge live = network.getEdge(snapshot.getId());
            if (live == null) {
                restoreEdge(snapshot);
                continue;
            }
            // Relink back to absorbed if still present.
            RoadNode absorbed = network.getNode(absorbedId);
            if (absorbed == null) {
                continue;
            }
            String survivorId = live.getStartNodeId().equals(absorbedId)
                ? live.getEndNodeId()
                : (live.getEndNodeId().equals(absorbedId) ? live.getStartNodeId() : null);
            // Prefer restoring geometry from snapshot by detach+reattach.
            network.unassignEdgeFromRoad(live.getId());
            network.detachEdge(live.getId());
            restoreEdge(snapshot);
        }
        succeeded.clear();
    }

    private boolean canRelinkEdgeEndpoint(RoadEdge edge, String oldNodeId, String newNodeId) {
        if (oldNodeId.equals(newNodeId) || edge == null) {
            return false;
        }
        RoadNode oldNode = network.getNode(oldNodeId);
        RoadNode newNode = network.getNode(newNodeId);
        if (oldNode == null || newNode == null) {
            return false;
        }
        if (!edge.getStartNodeId().equals(oldNodeId) && !edge.getEndNodeId().equals(oldNodeId)) {
            return false;
        }
        List<Vec2d> centerline = new ArrayList<>(edge.getCenterlinePoints());
        if (centerline.size() < 2) {
            return false;
        }
        Vec2d newPosition = newNode.getPosition().copy();
        if (edge.getStartNodeId().equals(oldNodeId)) {
            centerline.set(0, newPosition);
        } else {
            centerline.set(centerline.size() - 1, newPosition);
        }
        return RoadGeometryUtils.calculatePathLength(centerline)
            >= RoadNetworkInvariantValidator.GEOMETRY_ENDPOINT_TOLERANCE_METERS;
    }

    private boolean hasParallelEdge(
            String nodeA,
            String nodeB,
            String roadId,
            String excludeEdgeId) {
        for (RoadEdge edge : network.getEdgesAtNode(nodeA)) {
            if (edge == null || edge.getId().equals(excludeEdgeId)) {
                continue;
            }
            if (roadId == null || !roadId.equals(edge.getRoadId())) {
                continue;
            }
            String other = otherEndpoint(edge, nodeA);
            if (nodeB.equals(other)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Relink an edge endpoint to another node and sync the matching centerline vertex.
     * Removes the old node when it becomes degree-0.
     */
    public boolean relinkEdgeEndpoint(String edgeId, String oldNodeId, String newNodeId) {
        RoadEdge edge = network.getEdge(edgeId);
        if (edge == null) {
            return false;
        }
        boolean relinked = relinkEdgeEndpoint(edge, oldNodeId, newNodeId);
        if (relinked) {
            RoadNode oldNode = network.getNode(oldNodeId);
            if (oldNode != null && oldNode.getDegree() == 0 && !oldNodeId.equals(newNodeId)) {
                network.removeNode(oldNodeId);
            }
        }
        return relinked;
    }

    private boolean relinkEdgeEndpoint(RoadEdge edge, String oldNodeId, String newNodeId) {
        if (oldNodeId.equals(newNodeId)) {
            return false;
        }
        RoadNode oldNode = network.getNode(oldNodeId);
        RoadNode newNode = network.getNode(newNodeId);
        if (oldNode == null || newNode == null) {
            return false;
        }
        Vec2d newPosition = newNode.getPosition().copy();
        List<Vec2d> centerline = new ArrayList<>(edge.getCenterlinePoints());
        if (centerline.isEmpty()) {
            centerline = new ArrayList<>(List.of(newPosition.copy(), newPosition.copy()));
        }
        if (edge.getStartNodeId().equals(oldNodeId)) {
            centerline.set(0, newPosition);
            if (RoadGeometryUtils.calculatePathLength(centerline)
                    < RoadNetworkInvariantValidator.GEOMETRY_ENDPOINT_TOLERANCE_METERS) {
                return false;
            }
            oldNode.removeEdge(edge.getId());
            edge.setStartNodeId(newNodeId);
            edge.setCenterlinePoints(centerline);
            newNode.addEdge(edge.getId());
            return true;
        }
        if (edge.getEndNodeId().equals(oldNodeId)) {
            centerline.set(centerline.size() - 1, newPosition);
            if (RoadGeometryUtils.calculatePathLength(centerline)
                    < RoadNetworkInvariantValidator.GEOMETRY_ENDPOINT_TOLERANCE_METERS) {
                return false;
            }
            oldNode.removeEdge(edge.getId());
            edge.setEndNodeId(newNodeId);
            edge.setCenterlinePoints(centerline);
            newNode.addEdge(edge.getId());
            return true;
        }
        return false;
    }

    /**
     * Edge split lineage: {@code oldEdgeId} → ({@code firstEdgeId}, {@code secondEdgeId})
     * at geometry-local distance {@code splitGeometryLocal}.
     */
    public record SplitResult(
            String firstEdgeId,
            String secondEdgeId,
            String nodeId,
            String oldEdgeId,
            double splitGeometryLocal) {

        public SplitResult(String firstEdgeId, String secondEdgeId, String nodeId) {
            this(firstEdgeId, secondEdgeId, nodeId, null, Double.NaN);
        }
    }

    /**
     * Edge merge lineage: two old edges → one merged edge.
     * {@code secondLocalOffset} is the geometry-local start of the second old edge along the
     * merged polyline. {@code *Reversed} means the old edge's storage direction was mirrored
     * into the merged polyline (local distance must use {@code L - d}).
     */
    public record MergeResult(
            String mergedEdgeId,
            String firstOldEdgeId,
            String secondOldEdgeId,
            double secondLocalOffset,
            double firstOldLength,
            double secondOldLength,
            boolean firstReversed,
            boolean secondReversed) {
    }
}
