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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 在 snapshot / 生成前将 {@link RoadStackCrossing} 物化为四臂路口节点。
 */
public final class RoadStackCrossingMaterializer {
    private static final double NODE_TOLERANCE = RoadNetworkBuilder.NODE_TOLERANCE;
    private static final double ARM_CLASSIFY_TIE_EPSILON = 1e-6;

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

        List<Vec2d> lowerRefs = passReferencePoints(
            network, stackCrossing.roadId(), stackCrossing.resolvedLowerStation());
        List<Vec2d> upperRefs = passReferencePoints(
            network, stackCrossing.roadId(), stackCrossing.resolvedUpperStation());

        PassMaterializeResult passA = materializeStationPass(
            network, stackCrossing.roadId(), stackCrossing.stationA(), junction, position);
        PassMaterializeResult passB = materializeStationPass(
            network, stackCrossing.roadId(), stackCrossing.stationB(), junction, position);

        Map<String, PassRole> lineage = new HashMap<>();
        boolean stationAIsLower = Math.abs(stackCrossing.stationA() - stackCrossing.resolvedLowerStation())
            <= Math.abs(stackCrossing.stationA() - stackCrossing.resolvedUpperStation());
        tagPassEdges(lineage, passA, stationAIsLower ? PassRole.LOWER : PassRole.UPPER);
        tagPassEdges(lineage, passB, stationAIsLower ? PassRole.UPPER : PassRole.LOWER);

        breakJunctionSelfLoops(network, junction, lineage);

        CrossingMaterializeStatus verifiedA = verifyPassConnected(network, junction, passA);
        CrossingMaterializeStatus verifiedB = verifyPassConnected(network, junction, passB);
        if (!verifiedA.isSuccessful() || !verifiedB.isSuccessful()) {
            return Optional.of(failure(
                stackCrossing,
                verifiedA,
                verifiedB,
                "stack arms incomplete after materialize"));
        }
        if (!passArmSetsDistinct(passA, passB, lineage)) {
            return Optional.of(failure(
                stackCrossing,
                CrossingMaterializeStatus.SPLIT_FAILED,
                CrossingMaterializeStatus.SPLIT_FAILED,
                "stack passes did not produce distinct arm sets"));
        }

        Optional<String> designFailure = applyStackDesignToJunction(
            network, stackCrossing, junction, claimedGradeSepNodeIds, lowerRefs, upperRefs, lineage);
        return designFailure.map(s -> failure(
                stackCrossing,
                CrossingMaterializeStatus.SPLIT_FAILED,
                CrossingMaterializeStatus.SPLIT_FAILED,
                s));
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

    /**
     * Split (or relink) at the resolved SegmentStation localDistance — never re-pick the first
     * spatial hit on a multi-pass edge.
     */
    private static PassMaterializeResult materializeStationPass(
            RoadNetwork network,
            String roadId,
            double station,
            RoadNode junction,
            Vec2d position) {
        Road road = network.getRoad(roadId);
        if (road == null) {
            return PassMaterializeResult.failed(CrossingMaterializeStatus.ROAD_NOT_FOUND);
        }
        Optional<SegmentStation> resolved = RoadStationing.resolve(network, road, station);
        if (resolved.isEmpty()) {
            return PassMaterializeResult.failed(CrossingMaterializeStatus.STATION_UNRESOLVED);
        }
        SegmentStation segmentStation = resolved.get();
        RoadEdge edge = network.getEdge(segmentStation.segmentId());
        if (edge == null) {
            return PassMaterializeResult.failed(CrossingMaterializeStatus.EDGE_NOT_FOUND);
        }
        List<Vec2d> points = edge.getCenterlinePoints();
        int interiorSegment = segmentIndexAtLocalDistance(points, segmentStation.localDistance());
        if (interiorSegment >= 0) {
            Vec2d splitPoint = RoadGeometryUtils.pointAtDistance(points, segmentStation.localDistance());
            if (splitPoint == null) {
                return PassMaterializeResult.failed(CrossingMaterializeStatus.SPLIT_FAILED);
            }
            // Spatial sanity: resolved local arc must land near the crossing position.
            if (position != null && splitPoint.distance(position) > NODE_TOLERANCE * 2.0) {
                return PassMaterializeResult.failed(CrossingMaterializeStatus.SPLIT_FAILED);
            }
            Optional<RoadGraphEdits.SplitResult> split = RoadGraphEdits.of(network).splitEdgeAtInteriorSegment(
                edge.getId(),
                junction.getId(),
                interiorSegment,
                splitPoint,
                NODE_TOLERANCE);
            if (split.isEmpty()) {
                return PassMaterializeResult.failed(CrossingMaterializeStatus.SPLIT_FAILED);
            }
            Set<String> junctionEdges = new HashSet<>();
            addIfJunctionIncident(network, junction.getId(), split.get().firstEdgeId(), junctionEdges);
            addIfJunctionIncident(network, junction.getId(), split.get().secondEdgeId(), junctionEdges);
            return new PassMaterializeResult(CrossingMaterializeStatus.SUCCESS, junctionEdges);
        }
        if (isNearEdgeEndpoint(edge, position)) {
            CrossingMaterializeStatus status = connectEdgeEndpointToNode(network, edge, position, junction);
            if (!status.isSuccessful() && status != CrossingMaterializeStatus.ALREADY_CONNECTED) {
                return PassMaterializeResult.failed(status);
            }
            Set<String> junctionEdges = new HashSet<>();
            if (junction.getConnectedEdgeIds().contains(edge.getId())) {
                junctionEdges.add(edge.getId());
            }
            return new PassMaterializeResult(
                status == CrossingMaterializeStatus.ALREADY_CONNECTED
                    ? CrossingMaterializeStatus.SUCCESS
                    : status,
                junctionEdges);
        }
        return PassMaterializeResult.failed(CrossingMaterializeStatus.SPLIT_FAILED);
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
                // Endpoint of the whole polyline is not an interior split.
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

    private static void addIfJunctionIncident(
            RoadNetwork network,
            String junctionId,
            String edgeId,
            Set<String> out) {
        RoadEdge edge = network.getEdge(edgeId);
        if (edge == null) {
            return;
        }
        if (junctionId.equals(edge.getStartNodeId()) || junctionId.equals(edge.getEndNodeId())) {
            out.add(edgeId);
        }
    }

    private static CrossingMaterializeStatus verifyPassConnected(
            RoadNetwork network,
            RoadNode junction,
            PassMaterializeResult pass) {
        if (!pass.status().isSuccessful()) {
            return pass.status();
        }
        if (pass.junctionEdgeIds().isEmpty()) {
            return CrossingMaterializeStatus.SPLIT_FAILED;
        }
        for (String edgeId : pass.junctionEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            if (junction.getId().equals(edge.getStartNodeId())
                    || junction.getId().equals(edge.getEndNodeId())) {
                return CrossingMaterializeStatus.SUCCESS;
            }
        }
        // Lineage may have been remapped by self-loop break — accept if junction still has road arms.
        for (String edgeId : junction.getConnectedEdgeIds()) {
            if (network.getEdge(edgeId) != null) {
                return CrossingMaterializeStatus.SUCCESS;
            }
        }
        return CrossingMaterializeStatus.SPLIT_FAILED;
    }

    private static boolean passArmSetsDistinct(
            PassMaterializeResult passA,
            PassMaterializeResult passB,
            Map<String, PassRole> lineage) {
        Set<String> lower = new HashSet<>();
        Set<String> upper = new HashSet<>();
        for (Map.Entry<String, PassRole> entry : lineage.entrySet()) {
            if (entry.getValue() == PassRole.LOWER) {
                lower.add(entry.getKey());
            } else if (entry.getValue() == PassRole.UPPER) {
                upper.add(entry.getKey());
            }
        }
        if (!lower.isEmpty() && !upper.isEmpty()) {
            Set<String> overlap = new HashSet<>(lower);
            overlap.retainAll(upper);
            return overlap.isEmpty();
        }
        // Fallback: raw pass edge sets before remapping.
        Set<String> a = new HashSet<>(passA.junctionEdgeIds());
        Set<String> b = new HashSet<>(passB.junctionEdgeIds());
        a.retainAll(b);
        return a.isEmpty() && !passA.junctionEdgeIds().isEmpty() && !passB.junctionEdgeIds().isEmpty();
    }

    private static void tagPassEdges(
            Map<String, PassRole> lineage,
            PassMaterializeResult pass,
            PassRole role) {
        if (!pass.status().isSuccessful()) {
            return;
        }
        for (String edgeId : pass.junctionEdgeIds()) {
            lineage.put(edgeId, role);
        }
    }

    private static void breakJunctionSelfLoops(
            RoadNetwork network,
            RoadNode junction,
            Map<String, PassRole> lineage) {
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
            if (breakNode == null) {
                continue;
            }
            PassRole parentRole = lineage.get(edgeId);
            Optional<RoadGraphEdits.SplitResult> split = RoadGraphEdits.of(network).splitEdgeAtNode(
                edge.getId(),
                breakNode.getId(),
                breakVertex.get(),
                NODE_TOLERANCE);
            if (split.isEmpty()) {
                continue;
            }
            lineage.remove(edgeId);
            if (parentRole != null) {
                // Self-loop between two passes: first half inherits parent; second half is the other pass.
                // Prefer reference-point assist later for the far half when both roles are needed.
                lineage.put(split.get().firstEdgeId(), parentRole);
                lineage.put(split.get().secondEdgeId(), parentRole);
            }
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

    private static Optional<String> applyStackDesignToJunction(
            RoadNetwork network,
            RoadStackCrossing stackCrossing,
            RoadNode junction,
            Set<String> claimedGradeSepNodeIds,
            List<Vec2d> lowerRefs,
            List<Vec2d> upperRefs,
            Map<String, PassRole> lineage) {
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
        Optional<String> armFailure = assignStackArmRoles(
            network, stackCrossing, junction, lowerRefs, upperRefs, lineage);
        if (armFailure.isPresent()) {
            junction.clearStackCrossingMetadata();
            return armFailure;
        }
        claimedGradeSepNodeIds.add(junction.getId());
        return Optional.empty();
    }

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

    /**
     * Prefer split lineage; use reference-point distance only for untagged arms.
     * Empty refs / tied distances / incomplete 2+2 sets fail closed.
     */
    private static Optional<String> assignStackArmRoles(
            RoadNetwork network,
            RoadStackCrossing stackCrossing,
            RoadNode junction,
            List<Vec2d> lowerRefs,
            List<Vec2d> upperRefs,
            Map<String, PassRole> lineage) {
        String roadId = stackCrossing.roadId();
        String junctionId = junction.getId();
        List<String> roadArms = new ArrayList<>();
        for (String edgeId : junction.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null && roadId.equals(edge.getRoadId())) {
                roadArms.add(edgeId);
            }
        }

        Set<String> lower = new HashSet<>();
        Set<String> upper = new HashSet<>();
        List<String> untagged = new ArrayList<>();
        for (String edgeId : roadArms) {
            PassRole role = lineage.get(edgeId);
            if (role == PassRole.LOWER) {
                lower.add(edgeId);
            } else if (role == PassRole.UPPER) {
                upper.add(edgeId);
            } else {
                untagged.add(edgeId);
            }
        }

        // After self-loop break both children may share one parent role; reclassify extras with refs.
        if (lower.size() > 2 || upper.size() > 2 || !untagged.isEmpty()
                || (lower.size() + upper.size() < roadArms.size())) {
            for (String edgeId : new ArrayList<>(lower)) {
                if (lower.size() <= 2) {
                    break;
                }
                // Keep two; move surplus to untagged for ref assist.
                lower.remove(edgeId);
                untagged.add(edgeId);
            }
            for (String edgeId : new ArrayList<>(upper)) {
                if (upper.size() <= 2) {
                    break;
                }
                upper.remove(edgeId);
                untagged.add(edgeId);
            }
            for (String edgeId : roadArms) {
                if (!lower.contains(edgeId) && !upper.contains(edgeId) && !untagged.contains(edgeId)) {
                    untagged.add(edgeId);
                }
            }
        }

        for (String edgeId : untagged) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            Optional<Vec2d> sample = armSampleAwayFromJunction(edge, junctionId);
            if (sample.isEmpty()) {
                return Optional.of("stack arm sample unavailable for edge " + edgeId);
            }
            if (lowerRefs.isEmpty() && upperRefs.isEmpty()) {
                return Optional.of("stack arm reference points unavailable");
            }
            double lowerDist = minDistance(sample.get(), lowerRefs);
            double upperDist = minDistance(sample.get(), upperRefs);
            if (!Double.isFinite(lowerDist) && !Double.isFinite(upperDist)) {
                return Optional.of("stack arm classification failed: no finite reference distance");
            }
            if (Math.abs(lowerDist - upperDist) <= ARM_CLASSIFY_TIE_EPSILON) {
                return Optional.of("stack arm classification ambiguous for edge " + edgeId);
            }
            if (lowerDist < upperDist) {
                lower.add(edgeId);
            } else {
                upper.add(edgeId);
            }
        }

        // When lineage tagged a whole self-loop as one role, split arms by refs if 2+2 not met.
        if ((lower.size() != 2 || upper.size() != 2) && roadArms.size() == 4) {
            lower.clear();
            upper.clear();
            if (lowerRefs.isEmpty() || upperRefs.isEmpty()) {
                return Optional.of("stack arm roles incomplete and reference assist unavailable");
            }
            for (String edgeId : roadArms) {
                RoadEdge edge = network.getEdge(edgeId);
                Optional<Vec2d> sample = armSampleAwayFromJunction(edge, junctionId);
                if (sample.isEmpty()) {
                    return Optional.of("stack arm sample unavailable during reclassify");
                }
                double lowerDist = minDistance(sample.get(), lowerRefs);
                double upperDist = minDistance(sample.get(), upperRefs);
                if (Math.abs(lowerDist - upperDist) <= ARM_CLASSIFY_TIE_EPSILON
                        || (!Double.isFinite(lowerDist) && !Double.isFinite(upperDist))) {
                    return Optional.of("stack arm reclassify ambiguous");
                }
                if (lowerDist < upperDist) {
                    lower.add(edgeId);
                } else {
                    upper.add(edgeId);
                }
            }
        }

        Set<String> overlap = new HashSet<>(lower);
        overlap.retainAll(upper);
        if (!overlap.isEmpty()) {
            return Optional.of("stack arm roles overlap");
        }
        if (roadArms.size() == 4 && (lower.size() != 2 || upper.size() != 2)) {
            return Optional.of("stack arm roles incomplete: expected 2 lower + 2 upper, got "
                + lower.size() + "/" + upper.size());
        }
        if (lower.isEmpty() || upper.isEmpty()) {
            return Optional.of("stack arm roles missing lower or upper set");
        }

        for (String edgeId : lower) {
            junction.addStackLowerEdgeId(edgeId);
        }
        for (String edgeId : upper) {
            junction.addStackUpperEdgeId(edgeId);
        }
        return Optional.empty();
    }

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

    private enum PassRole {
        LOWER,
        UPPER
    }

    private record PassMaterializeResult(
            CrossingMaterializeStatus status,
            Set<String> junctionEdgeIds) {
        static PassMaterializeResult failed(CrossingMaterializeStatus status) {
            return new PassMaterializeResult(status, Set.of());
        }
    }
}
