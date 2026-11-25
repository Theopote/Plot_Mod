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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 在 snapshot / 生成前将 {@link RoadStackCrossing} 物化为四臂路口节点。
 */
public final class RoadStackCrossingMaterializer {
    private static final double NODE_TOLERANCE = RoadNetworkBuilder.NODE_TOLERANCE;

    private RoadStackCrossingMaterializer() {
    }

    public static CrossingMaterializationReport materializeAll(
            RoadNetwork network,
            Collection<RoadStackCrossing> stackCrossings) {
        if (network == null || stackCrossings == null || stackCrossings.isEmpty()) {
            return CrossingMaterializationReport.empty();
        }
        int attempted = 0;
        int succeeded = 0;
        List<CrossingMaterializationFailure> failures = new ArrayList<>();
        Set<String> claimedGradeSepNodeIds = new HashSet<>();
        for (RoadStackCrossing stackCrossing : stackCrossings) {
            attempted++;
            Optional<CrossingMaterializationFailure> failure =
                materializeStackCrossing(network, stackCrossing, claimedGradeSepNodeIds);
            if (failure.isPresent()) {
                failures.add(failure.get());
            } else {
                succeeded++;
            }
        }
        return new CrossingMaterializationReport(attempted, succeeded, failures);
    }

    public static IntersectionResult materializeInPlace(RoadNetwork network) {
        if (network == null || network.getStackCrossings().isEmpty()) {
            return IntersectionResult.COMPLETE;
        }
        return materializeAll(network, network.getStackCrossings().values()).toIntersectionResult();
    }

    private static Optional<CrossingMaterializationFailure> materializeStackCrossing(
            RoadNetwork network,
            RoadStackCrossing stackCrossing,
            Set<String> claimedGradeSepNodeIds) {
        Vec2d position = stackCrossing.position();
        if (position == null || stackCrossing.roadId() == null) {
            return Optional.of(failure(
                stackCrossing,
                CrossingMaterializeStatus.STATION_UNRESOLVED,
                CrossingMaterializeStatus.STATION_UNRESOLVED,
                "stack crossing position or roadId is null"));
        }

        // Preflight only validates resolvability; each pass re-resolves at apply time because
        // the first split invalidates the second pass's original edge id.
        CrossingMaterializeStatus preflightA = preflightStation(
            network, stackCrossing.roadId(), stackCrossing.stationA());
        CrossingMaterializeStatus preflightB = preflightStation(
            network, stackCrossing.roadId(), stackCrossing.stationB());
        if (!preflightA.isSuccessful() || !preflightB.isSuccessful()) {
            return Optional.of(failure(
                stackCrossing,
                preflightA,
                preflightB,
                "stack pass preflight failed"));
        }

        RoadNode junction = findOrCreateNode(network, position, stackCrossing, claimedGradeSepNodeIds);
        if (junction == null) {
            return Optional.of(failure(
                stackCrossing,
                CrossingMaterializeStatus.SPLIT_FAILED,
                CrossingMaterializeStatus.SPLIT_FAILED,
                "unsupported multi-layer stack at shared node"));
        }

        // Capture pass reference points while the road is still stationable (pre-split).
        // After the four-arm junction exists the graph branches and chainageAtPosition is unreliable.
        List<Vec2d> lowerRefs = passReferencePoints(
            network, stackCrossing.roadId(), stackCrossing.resolvedLowerStation());
        List<Vec2d> upperRefs = passReferencePoints(
            network, stackCrossing.roadId(), stackCrossing.resolvedUpperStation());

        CrossingMaterializeStatus armA = materializeStationPass(
            network, stackCrossing.roadId(), stackCrossing.stationA(), junction, position);
        CrossingMaterializeStatus armB = materializeStationPass(
            network, stackCrossing.roadId(), stackCrossing.stationB(), junction, position);
        breakJunctionSelfLoops(network, junction);

        CrossingMaterializeStatus verifiedA = verifyPassConnected(network, junction, stackCrossing.roadId(), armA);
        CrossingMaterializeStatus verifiedB = verifyPassConnected(network, junction, stackCrossing.roadId(), armB);
        if (!verifiedA.isSuccessful() || !verifiedB.isSuccessful()) {
            return Optional.of(failure(
                stackCrossing,
                verifiedA,
                verifiedB,
                "stack arms incomplete after materialize"));
        }

        Optional<String> designFailure = applyStackDesignToJunction(
            network, stackCrossing, junction, claimedGradeSepNodeIds, lowerRefs, upperRefs);
        if (designFailure.isPresent()) {
            return Optional.of(failure(
                stackCrossing,
                CrossingMaterializeStatus.SPLIT_FAILED,
                CrossingMaterializeStatus.SPLIT_FAILED,
                designFailure.get()));
        }
        return Optional.empty();
    }

    private static CrossingMaterializeStatus preflightStation(
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

    private static CrossingMaterializeStatus materializeStationPass(
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
        RoadEdge edge = network.getEdge(resolved.get().segmentId());
        if (edge == null) {
            return CrossingMaterializeStatus.EDGE_NOT_FOUND;
        }
        int interiorSegment = findInteriorPassSegmentIndex(edge.getCenterlinePoints(), position);
        if (interiorSegment >= 0) {
            Vec2d projected = RoadGeometryUtils.projectPointOnSegment(
                edge.getCenterlinePoints().get(interiorSegment),
                edge.getCenterlinePoints().get(interiorSegment + 1),
                position);
            Optional<RoadGraphEdits.SplitResult> split = RoadGraphEdits.of(network).splitEdgeAtInteriorSegment(
                edge.getId(),
                junction.getId(),
                interiorSegment,
                projected,
                NODE_TOLERANCE);
            return split.isPresent()
                ? CrossingMaterializeStatus.SUCCESS
                : CrossingMaterializeStatus.SPLIT_FAILED;
        }
        if (isNearEdgeEndpoint(edge, position)) {
            return connectEdgeEndpointToNode(network, edge, position, junction);
        }
        return CrossingMaterializeStatus.SPLIT_FAILED;
    }

    private static CrossingMaterializeStatus verifyPassConnected(
            RoadNetwork network,
            RoadNode junction,
            String roadId,
            CrossingMaterializeStatus status) {
        if (!status.isSuccessful()) {
            return status;
        }
        for (String edgeId : junction.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null && roadId.equals(edge.getRoadId())) {
                return status;
            }
        }
        return CrossingMaterializeStatus.SPLIT_FAILED;
    }

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
            RoadNode breakNode = findOrCreateNode(network, breakVertex.get(), null, null);
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

    private static Optional<String> applyStackDesignToJunction(
            RoadNetwork network,
            RoadStackCrossing stackCrossing,
            RoadNode junction,
            Set<String> claimedGradeSepNodeIds,
            List<Vec2d> lowerRefs,
            List<Vec2d> upperRefs) {
        if (stackCrossing.sharedElevation() != null
                && VerticalProfileGenerationPolicy.isStackCrossingSharedElevationAuthoritative(
                    network, stackCrossing)) {
            junction.setManualElevation(
                stackCrossing.sharedElevation(),
                com.plot.plugin.road.model.ManualElevationOrigin.CROSSING);
        }
        if (!stackCrossing.isGradeSeparated()) {
            if (stackCrossing.id().equals(junction.getStackCrossingId())) {
                junction.clearStackCrossingMetadata();
            }
            return Optional.empty();
        }
        String existingId = junction.getStackCrossingId();
        if (existingId != null
                && !existingId.isBlank()
                && !existingId.equals(stackCrossing.id())
                && junction.isStackGradeSeparated()) {
            return Optional.of("node already bound to another grade-separated stack crossing");
        }
        if (claimedGradeSepNodeIds.contains(junction.getId())
                && !stackCrossing.id().equals(junction.getStackCrossingId())) {
            return Optional.of("multiple grade-separated stacks cannot share one node");
        }
        junction.clearStackCrossingMetadata();
        junction.setStackCrossingId(stackCrossing.id());
        junction.setStackGradeSeparated(true);
        if (stackCrossing.crossingClearance() != null) {
            junction.setStackCrossingClearance(stackCrossing.crossingClearance());
        }
        assignStackArmRoles(network, stackCrossing, junction, lowerRefs, upperRefs);
        claimedGradeSepNodeIds.add(junction.getId());
        return Optional.empty();
    }

    /**
     * Points slightly before/after a pass station on the pre-split road. Used to classify
     * post-split junction arms without relying on post-branch chainage.
     */
    private static List<Vec2d> passReferencePoints(RoadNetwork network, String roadId, double station) {
        Road road = network.getRoad(roadId);
        if (road == null) {
            return List.of();
        }
        double length = RoadStationing.canonicalLength(network, road);
        double delta = Math.min(1.0, Math.max(0.25, length * 0.02));
        List<Vec2d> refs = new ArrayList<>(2);
        RoadStationing.pointAtStation(network, road, Math.max(0.0, station - delta)).ifPresent(refs::add);
        RoadStationing.pointAtStation(network, road, Math.min(length, station + delta)).ifPresent(refs::add);
        return List.copyOf(refs);
    }

    private static void assignStackArmRoles(
            RoadNetwork network,
            RoadStackCrossing stackCrossing,
            RoadNode junction,
            List<Vec2d> lowerRefs,
            List<Vec2d> upperRefs) {
        String roadId = stackCrossing.roadId();
        String junctionId = junction.getId();
        for (String edgeId : junction.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null || !roadId.equals(edge.getRoadId())) {
                continue;
            }
            Optional<Vec2d> sample = armSampleAwayFromJunction(edge, junctionId);
            if (sample.isEmpty()) {
                continue;
            }
            double lowerDist = minDistance(sample.get(), lowerRefs);
            double upperDist = minDistance(sample.get(), upperRefs);
            if (lowerDist <= upperDist) {
                junction.addStackLowerEdgeId(edgeId);
            } else {
                junction.addStackUpperEdgeId(edgeId);
            }
        }
    }

    /**
     * Sample a point slightly away from the junction along the arm.
     * Pass identity must not depend on Edge start/end storage direction.
     */
    private static Optional<Vec2d> armSampleAwayFromJunction(RoadEdge edge, String junctionId) {
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.size() < 2) {
            return Optional.empty();
        }
        boolean junctionAtStart = edge.getStartNodeId().equals(junctionId);
        boolean junctionAtEnd = edge.getEndNodeId().equals(junctionId);
        if (!junctionAtStart && !junctionAtEnd) {
            return Optional.empty();
        }
        Vec2d from = junctionAtStart ? points.getFirst() : points.getLast();
        Vec2d toward = junctionAtStart ? points.get(1) : points.get(points.size() - 2);
        double length = from.distance(toward);
        if (length <= 1e-9) {
            return Optional.empty();
        }
        // Stay close enough to remain on this arm, far enough to escape the crossing node.
        double t = Math.min(0.35, Math.max(0.08, 1.0 / length));
        return Optional.of(from.lerp(toward, t));
    }

    private static double minDistance(Vec2d sample, List<Vec2d> refs) {
        if (sample == null || refs == null || refs.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        double best = Double.POSITIVE_INFINITY;
        for (Vec2d ref : refs) {
            if (ref != null) {
                best = Math.min(best, sample.distance(ref));
            }
        }
        return best;
    }

    private static RoadNode findOrCreateNode(
            RoadNetwork network,
            Vec2d position,
            RoadStackCrossing stackCrossing,
            Set<String> claimedGradeSepNodeIds) {
        for (RoadNode node : network.getNodes().values()) {
            if (!RoadGeometryUtils.pointsNear(node.getPosition(), position, NODE_TOLERANCE)) {
                continue;
            }
            if (stackCrossing != null
                    && stackCrossing.isGradeSeparated()
                    && node.isStackGradeSeparated()
                    && node.getStackCrossingId() != null
                    && !node.getStackCrossingId().equals(stackCrossing.id())) {
                return null;
            }
            if (stackCrossing != null
                    && stackCrossing.isGradeSeparated()
                    && claimedGradeSepNodeIds != null
                    && claimedGradeSepNodeIds.contains(node.getId())
                    && !stackCrossing.id().equals(node.getStackCrossingId())) {
                return null;
            }
            return node;
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
        if (endpointNodeId == null || endpointNodeId.equals(junctionNode.getId())) {
            return CrossingMaterializeStatus.ALREADY_CONNECTED;
        }
        return mergeEdgeEndpointToNode(network, edge, endpointNodeId, junctionNode.getId())
            ? CrossingMaterializeStatus.SUCCESS
            : CrossingMaterializeStatus.ENDPOINT_RELINK_FAILED;
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

    private static CrossingMaterializationFailure failure(
            RoadStackCrossing stack,
            CrossingMaterializeStatus armA,
            CrossingMaterializeStatus armB,
            String detail) {
        return new CrossingMaterializationFailure(
            stack.id(),
            stack.roadId(),
            stack.roadId(),
            armA,
            armB,
            detail);
    }
}
