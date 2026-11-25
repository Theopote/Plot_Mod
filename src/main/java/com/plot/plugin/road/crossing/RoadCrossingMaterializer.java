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
import com.plot.plugin.road.pipeline.profile.VerticalProfileGenerationPolicy;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.station.SegmentStation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 在 snapshot / 生成前将已注册 {@link RoadCrossing} 临时物化为共享节点拓扑，兼容旧 junction 管线。
 * <p>
 * 单臂失败不会再被静默当成 COMPLETE：每条 Crossing 必须确认双道路均已接入节点。
 */
public final class RoadCrossingMaterializer {
    private static final double NODE_TOLERANCE = RoadNetworkBuilder.NODE_TOLERANCE;

    private RoadCrossingMaterializer() {
    }

    public static RoadNetwork materializeForSnapshot(RoadNetwork source) {
        return materializeForSnapshotWithReport(source).network();
    }

    public static SnapshotMaterialization materializeForSnapshotWithReport(RoadNetwork source) {
        if (source == null) {
            return new SnapshotMaterialization(new RoadNetwork(), CrossingMaterializationReport.empty());
        }
        if (source.getCrossings().isEmpty() && source.getStackCrossings().isEmpty()) {
            return new SnapshotMaterialization(source.snapshot(), CrossingMaterializationReport.empty());
        }
        // 物化在 snapshot 上进行；source 保留分裂前拓扑，供编辑纵断面重绑定。
        RoadNetwork snapshot = source.snapshot();
        CrossingMaterializationReport crossingReport =
            materializeAllCrossings(snapshot, source.getCrossings().values());
        CrossingMaterializationReport stackReport =
            RoadStackCrossingMaterializer.materializeAll(snapshot, source.getStackCrossings().values());
        preserveEditedProfilesAcrossSplits(source, snapshot);
        return new SnapshotMaterialization(snapshot, crossingReport.merge(stackReport));
    }

    /**
     * 调用方已持有与 live 隔离的工作副本时使用：就地物化，不再二次 {@link RoadNetwork#snapshot()}。
     * 空交叉注册表时直接返回同一实例。
     * <p>
     * 若有 EDITED 纵断面，会先拷一份分裂前拓扑供 {@link #preserveEditedProfilesAcrossSplits}；
     * 不可把同一 post-split 实例同时当作 source/snapshot，否则签名会因分裂而判 stale 并跳过重绑定。
     */
    public static SnapshotMaterialization materializeOwnedWorkingCopyWithReport(RoadNetwork workingCopy) {
        if (workingCopy == null) {
            return new SnapshotMaterialization(new RoadNetwork(), CrossingMaterializationReport.empty());
        }
        if (workingCopy.getCrossings().isEmpty() && workingCopy.getStackCrossings().isEmpty()) {
            return new SnapshotMaterialization(workingCopy, CrossingMaterializationReport.empty());
        }
        RoadNetwork preSplit = null;
        for (Road road : workingCopy.getRoads().values()) {
            if (com.plot.plugin.road.vertical.ResolvedProfileAuthority.usesEditedProfileForGeneration(
                    workingCopy, road)) {
                preSplit = workingCopy.deepCopy();
                break;
            }
        }
        CrossingMaterializationReport crossingReport =
            materializeAllCrossings(workingCopy, workingCopy.getCrossings().values());
        CrossingMaterializationReport stackReport =
            RoadStackCrossingMaterializer.materializeAll(workingCopy, workingCopy.getStackCrossings().values());
        if (preSplit != null) {
            preserveEditedProfilesAcrossSplits(preSplit, workingCopy);
        }
        return new SnapshotMaterialization(workingCopy, crossingReport.merge(stackReport));
    }

    /** Splitting instance edges at a crossing does not edit the logical road path. */
    private static void preserveEditedProfilesAcrossSplits(RoadNetwork source, RoadNetwork snapshot) {
        for (Road road : source.getRoads().values()) {
            if (!com.plot.plugin.road.vertical.ResolvedProfileAuthority.usesEditedProfileForGeneration(source, road)) continue;
            Road copy = snapshot.getRoad(road.getId());
            if (copy == null || !RoadStationing.isStationable(snapshot, copy)) continue;
            double length = RoadStationing.canonicalLength(source, road);
            if (Math.abs(length - RoadStationing.canonicalLength(snapshot, copy)) > 1e-5) continue;
            boolean samePath = true;
            for (double fraction : new double[] {0, 0.25, 0.5, 0.75, 1}) {
                var before = com.plot.plugin.road.alignment.RoadPlanGeometry.pointAtStation(source, road, length * fraction);
                var after = com.plot.plugin.road.alignment.RoadPlanGeometry.pointAtStation(snapshot, copy, length * fraction);
                if (before.isEmpty() || after.isEmpty() || before.get().distance(after.get()) > 1e-5) {
                    samePath = false;
                    break;
                }
            }
            if (!samePath) continue;
            copy.setVerticalAlignment(road.getVerticalAlignment().copy());
            copy.setProfileSource(com.plot.plugin.road.vertical.ProfileSource.EDITED);
            copy.setEditedGeometrySignature(com.plot.plugin.road.vertical.EditedProfileGeometry.signature(snapshot, copy));
        }
    }

    public static IntersectionResult materializeInPlace(RoadNetwork network) {
        if (network == null || network.getCrossings().isEmpty()) {
            return IntersectionResult.COMPLETE;
        }
        return materializeAllCrossings(network, List.copyOf(network.getCrossings().values()))
            .toIntersectionResult();
    }

    private static CrossingMaterializationReport materializeAllCrossings(
            RoadNetwork network,
            Iterable<RoadCrossing> crossings) {
        int attempted = 0;
        int succeeded = 0;
        List<CrossingMaterializationFailure> failures = new ArrayList<>();
        for (RoadCrossing crossing : crossings) {
            attempted++;
            Optional<CrossingMaterializationFailure> failure = materializeCrossing(network, crossing);
            if (failure.isPresent()) {
                failures.add(failure.get());
            } else {
                succeeded++;
            }
        }
        return new CrossingMaterializationReport(attempted, succeeded, failures);
    }

    private static Optional<CrossingMaterializationFailure> materializeCrossing(
            RoadNetwork network,
            RoadCrossing crossing) {
        Vec2d position = crossing.position();
        if (position == null) {
            return Optional.of(new CrossingMaterializationFailure(
                crossing.id(),
                crossing.roadAId(),
                crossing.roadBId(),
                CrossingMaterializeStatus.STATION_UNRESOLVED,
                CrossingMaterializeStatus.STATION_UNRESOLVED,
                "crossing position is null"));
        }
        CrossingMaterializeStatus preflightA = preflightRoadAtCrossing(
            network, crossing.roadAId(), crossing.stationA());
        CrossingMaterializeStatus preflightB = preflightRoadAtCrossing(
            network, crossing.roadBId(), crossing.stationB());
        if (!preflightA.isSuccessful() || !preflightB.isSuccessful()) {
            return Optional.of(new CrossingMaterializationFailure(
                crossing.id(),
                crossing.roadAId(),
                crossing.roadBId(),
                preflightA,
                preflightB,
                "crossing preflight failed: A=" + preflightA + ", B=" + preflightB));
        }
        RoadNode junction = findOrCreateNode(network, position);
        CrossingMaterializeStatus armA = materializeRoadAtCrossing(
            network, crossing.roadAId(), crossing.stationA(), junction, position);
        CrossingMaterializeStatus armB = materializeRoadAtCrossing(
            network, crossing.roadBId(), crossing.stationB(), junction, position);

        CrossingMaterializeStatus verifiedA = verifyArm(
            network, junction, crossing.roadAId(), crossing.stationA(), position, armA);
        CrossingMaterializeStatus verifiedB = verifyArm(
            network, junction, crossing.roadBId(), crossing.stationB(), position, armB);
        if (!verifiedA.isSuccessful() || !verifiedB.isSuccessful()) {
            // Do not apply design attributes onto an incomplete junction.
            return Optional.of(new CrossingMaterializationFailure(
                crossing.id(),
                crossing.roadAId(),
                crossing.roadBId(),
                verifiedA,
                verifiedB,
                "crossing arms incomplete: A=" + verifiedA + ", B=" + verifiedB));
        }
        Optional<String> designFailure = applyCrossingDesignToJunction(network, crossing, junction);
        if (designFailure.isPresent()) {
            return Optional.of(new CrossingMaterializationFailure(
                crossing.id(),
                crossing.roadAId(),
                crossing.roadBId(),
                CrossingMaterializeStatus.SPLIT_FAILED,
                CrossingMaterializeStatus.SPLIT_FAILED,
                designFailure.get()));
        }
        return Optional.empty();
    }

    private static CrossingMaterializeStatus preflightRoadAtCrossing(
            RoadNetwork network,
            String roadId,
            double station) {
        Road road = network.getRoad(roadId);
        if (road == null) {
            return CrossingMaterializeStatus.ROAD_NOT_FOUND;
        }
        Optional<SegmentStation> resolved = RoadStationing.resolve(network, road, station);
        if (resolved.isEmpty()) {
            return CrossingMaterializeStatus.STATION_UNRESOLVED;
        }
        if (network.getEdge(resolved.get().segmentId()) == null) {
            return CrossingMaterializeStatus.EDGE_NOT_FOUND;
        }
        return CrossingMaterializeStatus.SUCCESS;
    }

    private static CrossingMaterializeStatus verifyArm(
            RoadNetwork network,
            RoadNode junction,
            String roadId,
            double station,
            Vec2d position,
            CrossingMaterializeStatus status) {
        if (!status.isSuccessful()) {
            return status;
        }
        if (status == CrossingMaterializeStatus.ALREADY_CONNECTED) {
            return status;
        }
        if (!isResolvedEdgeConnectedNearPosition(network, junction, roadId, station, position)) {
            return CrossingMaterializeStatus.SPLIT_FAILED;
        }
        return status;
    }

    /**
     * @return failure detail when grade design cannot be applied without overwriting another crossing
     */
    private static Optional<String> applyCrossingDesignToJunction(
            RoadNetwork network,
            RoadCrossing crossing,
            RoadNode junction) {
        if (crossing.type() == CrossingType.GRADE_SEPARATED) {
            Optional<String> conflict = gradeSeparationConflict(network, crossing, junction);
            if (conflict.isPresent()) {
                return conflict;
            }
            boolean applied = network.setNodeGradeSeparation(
                junction.getId(),
                true,
                crossing.elevatedRoadId(),
                crossing.crossingClearance());
            if (!applied) {
                boolean forced = network.applyCrossingGradeSeparation(
                    junction.getId(),
                    crossing.elevatedRoadId(),
                    crossing.crossingClearance(),
                    true);
                if (!forced) {
                    return Optional.of("failed to apply grade separation for crossing " + crossing.id());
                }
            } else if (crossing.crossingClearance() != null) {
                // Registry clearance is authoritative — replace, don't Math.max.
                junction.setCrossingClearance(crossing.crossingClearance());
            }
        } else if (crossing.type() == CrossingType.AT_GRADE) {
            clearGradeSeparationIfOwnedByCrossing(network, crossing, junction);
        }
        if (crossing.sharedElevation() != null
                && VerticalProfileGenerationPolicy.isCrossingSharedElevationAuthoritative(
                    network, crossing)) {
            junction.setManualElevation(
                crossing.sharedElevation(),
                com.plot.plugin.road.model.ManualElevationOrigin.CROSSING);
        }
        // 平交圆角：Crossing 显式值覆盖节点；null 保留快照节点半径（未设置≠强制关）。
        com.plot.plugin.road.JunctionCornerRadius.applyToJunctionNode(junction, crossing);
        return Optional.empty();
    }

    /**
     * Node model stores a single elevatedRoadId — refuse incompatible multi-layer grade designs
     * that would overwrite another GRADE_SEPARATED registry crossing at this node.
     */
    private static Optional<String> gradeSeparationConflict(
            RoadNetwork network,
            RoadCrossing crossing,
            RoadNode junction) {
        if (junction.isStackGradeSeparated()) {
            return Optional.of(
                "node already has stack grade separation incompatible with crossing "
                    + crossing.id());
        }
        if (!junction.isGradeSeparated()) {
            return Optional.empty();
        }
        String existingElevated = junction.getElevatedRoadId();
        String nextElevated = crossing.elevatedRoadId();
        if (existingElevated != null
                && !existingElevated.isBlank()
                && nextElevated != null
                && !nextElevated.isBlank()
                && existingElevated.equals(nextElevated)) {
            return Optional.empty();
        }
        // Another grade-separated crossing already owns this node with a different elevated road.
        boolean otherOwner = false;
        for (RoadCrossing other : network.getCrossings().values()) {
            if (other == null || other.id().equals(crossing.id())) {
                continue;
            }
            if (other.type() != CrossingType.GRADE_SEPARATED || other.position() == null) {
                continue;
            }
            if (!RoadGeometryUtils.pointsNear(
                    other.position(), junction.getPosition(), NODE_TOLERANCE)) {
                continue;
            }
            otherOwner = true;
            if (other.elevatedRoadId() != null
                    && !other.elevatedRoadId().isBlank()
                    && nextElevated != null
                    && !nextElevated.isBlank()
                    && !other.elevatedRoadId().equals(nextElevated)) {
                return Optional.of(
                    "incompatible multi-layer grade separation at shared node: existing elevated="
                        + other.elevatedRoadId() + " incoming elevated=" + nextElevated);
            }
        }
        // Same crossing updating elevated A→B: node elevated alone is not a foreign owner.
        if (!otherOwner) {
            return Optional.empty();
        }
        if (existingElevated != null
                && !existingElevated.isBlank()
                && nextElevated != null
                && !nextElevated.isBlank()
                && !existingElevated.equals(nextElevated)) {
            return Optional.of(
                "node elevatedRoadId=" + existingElevated
                    + " conflicts with crossing elevatedRoadId=" + nextElevated);
        }
        return Optional.empty();
    }

    private static void clearGradeSeparationIfOwnedByCrossing(
            RoadNetwork network,
            RoadCrossing crossing,
            RoadNode junction) {
        if (!junction.isGradeSeparated()) {
            return;
        }
        // Keep grade metadata when another grade-separated registry crossing still uses this node.
        for (RoadCrossing other : network.getCrossings().values()) {
            if (other == null || other.id().equals(crossing.id())) {
                continue;
            }
            if (other.type() != CrossingType.GRADE_SEPARATED || other.position() == null) {
                continue;
            }
            if (RoadGeometryUtils.pointsNear(
                    other.position(), junction.getPosition(), NODE_TOLERANCE)) {
                return;
            }
        }
        junction.clearGradeSeparation();
    }

    private static CrossingMaterializeStatus materializeRoadAtCrossing(
            RoadNetwork network,
            String roadId,
            double station,
            RoadNode junction,
            Vec2d position) {
        Road road = network.getRoad(roadId);
        if (road == null) {
            return CrossingMaterializeStatus.ROAD_NOT_FOUND;
        }
        Optional<SegmentStation> resolved = RoadStationing.resolve(network, road, station);
        if (resolved.isEmpty()) {
            return CrossingMaterializeStatus.STATION_UNRESOLVED;
        }
        String edgeId = resolved.get().segmentId();
        RoadEdge edge = network.getEdge(edgeId);
        if (edge == null) {
            return CrossingMaterializeStatus.EDGE_NOT_FOUND;
        }
        List<Vec2d> points = edge.getCenterlinePoints();
        double localDistance = resolved.get().localDistance();
        Vec2d splitPoint = RoadGeometryUtils.pointAtDistance(points, localDistance);
        if (splitPoint == null) {
            return CrossingMaterializeStatus.SPLIT_FAILED;
        }
        // Station→coordinate consistency before ALREADY_CONNECTED / endpoint / interior paths.
        if (splitPoint.distance(position) > NODE_TOLERANCE) {
            return CrossingMaterializeStatus.SPLIT_FAILED;
        }
        if (isStationAtConnectedJunctionEndpoint(network, junction, edge, points, localDistance, position)) {
            return CrossingMaterializeStatus.ALREADY_CONNECTED;
        }
        // Endpoint vs interior from local arc — not spatial isNearEdgeEndpoint alone.
        int segmentIndex = segmentIndexAtLocalDistance(points, localDistance);
        if (segmentIndex >= 0) {
            Optional<RoadGraphEdits.SplitResult> split = RoadGraphEdits.of(network).splitEdgeAtInteriorSegment(
                edgeId, junction.getId(), segmentIndex, splitPoint, NODE_TOLERANCE);
            if (split.isPresent()) {
                return CrossingMaterializeStatus.SUCCESS;
            }
            return CrossingMaterializeStatus.SPLIT_FAILED;
        }
        if (isLocalDistanceAtPolylineEnd(points, localDistance)) {
            return connectEdgeEndpointToNode(network, edge, splitPoint, junction);
        }
        return CrossingMaterializeStatus.SPLIT_FAILED;
    }

    /**
     * ALREADY_CONNECTED only when the resolved local station is the junction endpoint of this edge
     * (not merely that the edge touches the junction somewhere else).
     */
    private static boolean isStationAtConnectedJunctionEndpoint(
            RoadNetwork network,
            RoadNode junction,
            RoadEdge edge,
            List<Vec2d> points,
            double localDistance,
            Vec2d position) {
        if (network == null || junction == null || edge == null || position == null) {
            return false;
        }
        if (!RoadGeometryUtils.pointsNear(junction.getPosition(), position, NODE_TOLERANCE)) {
            return false;
        }
        boolean atStart = junction.getId().equals(edge.getStartNodeId());
        boolean atEnd = junction.getId().equals(edge.getEndNodeId());
        if (!atStart && !atEnd) {
            return false;
        }
        double total = RoadGeometryUtils.calculatePathLength(points);
        boolean stationAtStart = localDistance <= NODE_TOLERANCE;
        boolean stationAtEnd = localDistance >= total - NODE_TOLERANCE;
        return (atStart && stationAtStart) || (atEnd && stationAtEnd);
    }

    private static boolean isLocalDistanceAtPolylineEnd(List<Vec2d> points, double localDistance) {
        if (points == null || points.size() < 2 || !Double.isFinite(localDistance)) {
            return false;
        }
        double total = RoadGeometryUtils.calculatePathLength(points);
        return localDistance <= NODE_TOLERANCE || localDistance >= total - NODE_TOLERANCE;
    }

    private static boolean isResolvedEdgeConnectedNearPosition(
            RoadNetwork network,
            RoadNode junction,
            String roadId,
            double station,
            Vec2d position) {
        Road road = network.getRoad(roadId);
        if (road == null) {
            return false;
        }
        if (!RoadGeometryUtils.pointsNear(junction.getPosition(), position, NODE_TOLERANCE)) {
            return false;
        }
        Optional<SegmentStation> resolved = RoadStationing.resolve(network, road, station);
        if (resolved.isPresent()) {
            RoadEdge edge = network.getEdge(resolved.get().segmentId());
            if (edge != null) {
                List<Vec2d> points = edge.getCenterlinePoints();
                Vec2d stationPoint = RoadGeometryUtils.pointAtDistance(points, resolved.get().localDistance());
                if (stationPoint != null
                        && stationPoint.distance(position) <= NODE_TOLERANCE
                        && (junction.getId().equals(edge.getStartNodeId())
                            || junction.getId().equals(edge.getEndNodeId()))) {
                    return true;
                }
            }
        }
        // After split the original segment id is gone — accept a road edge whose junction
        // endpoint coincides with the crossing position (geometry, not Road-ID-only).
        for (String edgeId : junction.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null || !roadId.equals(edge.getRoadId())) {
                continue;
            }
            List<Vec2d> points = edge.getCenterlinePoints();
            if (points.size() < 2) {
                continue;
            }
            boolean atStart = junction.getId().equals(edge.getStartNodeId());
            Vec2d endpoint = atStart ? points.getFirst() : points.getLast();
            if (endpoint.distance(position) <= NODE_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    /** Package-visible helper for tests: any edge of {@code roadId} incident on the junction. */
    static boolean isRoadConnectedToJunction(RoadNetwork network, RoadNode junction, String roadId) {
        if (network == null || junction == null || roadId == null) {
            return false;
        }
        for (String edgeId : junction.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null && roadId.equals(edge.getRoadId())) {
                return true;
            }
        }
        return false;
    }

    private static RoadNode findOrCreateNode(RoadNetwork network, Vec2d position) {
        for (RoadNode node : network.getNodes().values()) {
            if (RoadGeometryUtils.pointsNear(node.getPosition(), position, NODE_TOLERANCE)) {
                return node;
            }
        }
        return network.createNode(position.copy());
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

    private static boolean isNearEdgeEndpoint(RoadEdge edge, Vec2d point) {
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.isEmpty()) {
            return true;
        }
        return RoadGeometryUtils.pointsNear(points.getFirst(), point, NODE_TOLERANCE)
            || RoadGeometryUtils.pointsNear(points.getLast(), point, NODE_TOLERANCE);
    }

    private static CrossingMaterializeStatus connectEdgeEndpointToNode(
            RoadNetwork network,
            RoadEdge edge,
            Vec2d intersection,
            RoadNode junctionNode) {
        String endpointNodeId = findEndpointNodeId(edge, intersection);
        if (endpointNodeId == null) {
            return CrossingMaterializeStatus.ENDPOINT_RELINK_FAILED;
        }
        if (endpointNodeId.equals(junctionNode.getId())) {
            return CrossingMaterializeStatus.ALREADY_CONNECTED;
        }
        if (!mergeEdgeEndpointToNode(network, edge, endpointNodeId, junctionNode.getId())) {
            return CrossingMaterializeStatus.ENDPOINT_RELINK_FAILED;
        }
        return CrossingMaterializeStatus.SUCCESS;
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

    private static boolean mergeEdgeEndpointToNode(
            RoadNetwork network,
            RoadEdge edge,
            String oldNodeId,
            String newNodeId) {
        if (oldNodeId.equals(newNodeId)) {
            return true;
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
}
