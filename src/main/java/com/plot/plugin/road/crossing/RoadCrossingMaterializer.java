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
        RoadNetwork snapshot = source.snapshot();
        CrossingMaterializationReport crossingReport = materializeAllCrossings(snapshot, source.getCrossings().values());
        RoadStackCrossingMaterializer.materializeAll(snapshot, source.getStackCrossings().values());
        preserveEditedProfilesAcrossSplits(source, snapshot);
        return new SnapshotMaterialization(snapshot, crossingReport);
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
        RoadNode junction = findOrCreateNode(network, position);
        CrossingMaterializeStatus armA = materializeRoadAtCrossing(
            network, crossing.roadAId(), crossing.stationA(), junction, position);
        CrossingMaterializeStatus armB = materializeRoadAtCrossing(
            network, crossing.roadBId(), crossing.stationB(), junction, position);

        CrossingMaterializeStatus verifiedA = verifyArm(network, junction, crossing.roadAId(), armA);
        CrossingMaterializeStatus verifiedB = verifyArm(network, junction, crossing.roadBId(), armB);
        if (!verifiedA.isSuccessful() || !verifiedB.isSuccessful()) {
            return Optional.of(new CrossingMaterializationFailure(
                crossing.id(),
                crossing.roadAId(),
                crossing.roadBId(),
                verifiedA,
                verifiedB,
                "crossing arms incomplete: A=" + verifiedA + ", B=" + verifiedB));
        }
        applyCrossingDesignToJunction(network, crossing, junction);
        return Optional.empty();
    }

    private static CrossingMaterializeStatus verifyArm(
            RoadNetwork network,
            RoadNode junction,
            String roadId,
            CrossingMaterializeStatus status) {
        if (!status.isSuccessful()) {
            return status;
        }
        if (!isRoadConnectedToJunction(network, junction, roadId)) {
            return CrossingMaterializeStatus.SPLIT_FAILED;
        }
        return status;
    }

    private static void applyCrossingDesignToJunction(
            RoadNetwork network,
            RoadCrossing crossing,
            RoadNode junction) {
        if (crossing.type() == CrossingType.GRADE_SEPARATED) {
            boolean applied = network.setNodeGradeSeparation(
                junction.getId(),
                true,
                crossing.elevatedRoadId(),
                crossing.crossingClearance());
            if (!applied) {
                network.applyCrossingGradeSeparation(
                    junction.getId(),
                    crossing.elevatedRoadId(),
                    crossing.crossingClearance());
            }
        }
        if (crossing.sharedElevation() != null
                && VerticalProfileGenerationPolicy.isCrossingSharedElevationAuthoritative(
                    network, crossing)) {
            junction.setManualElevation(
                crossing.sharedElevation(),
                com.plot.plugin.road.model.ManualElevationOrigin.CROSSING);
        }
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
        if (isRoadConnectedToJunction(network, junction, roadId)) {
            return CrossingMaterializeStatus.ALREADY_CONNECTED;
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
        if (isNearEdgeEndpoint(edge, position)) {
            return connectEdgeEndpointToNode(network, edge, position, junction);
        }
        Optional<RoadGraphEdits.SplitResult> split = RoadGraphEdits.of(network).splitEdgeAtNode(
            edgeId, junction.getId(), position, NODE_TOLERANCE);
        if (split.isPresent()) {
            return CrossingMaterializeStatus.SUCCESS;
        }
        if (isRoadConnectedToJunction(network, junction, roadId)) {
            return CrossingMaterializeStatus.ALREADY_CONNECTED;
        }
        return CrossingMaterializeStatus.SPLIT_FAILED;
    }

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
