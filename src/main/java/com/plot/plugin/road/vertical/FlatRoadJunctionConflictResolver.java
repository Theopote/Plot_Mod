package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Detects and explicitly resolves flat-road junction conflicts. */
public final class FlatRoadJunctionConflictResolver {
    private static final double EPSILON = 1e-6;
    private static final double DEFAULT_MAX_GRADE = 8.0;

    public record Conflict(String roadId, String nodeId, double roadElevation, double junctionElevation) { }

    public record FlatRoadAtJunction(String roadId, double baseElevation) { }

    public record FlatFlatConflict(String nodeId, List<FlatRoadAtJunction> roads) { }

    public record TransitionIssue(String roadId, int fromPviIndex, int toPviIndex, double actual, double limit) { }

    private FlatRoadJunctionConflictResolver() { }

    public static List<Conflict> find(RoadNetwork network) {
        if (network == null) return List.of();
        List<Conflict> conflicts = new ArrayList<>();
        for (Road road : network.getRoads().values()) {
            if (road.getVerticalMode() != RoadVerticalMode.FLAT
                    || !RoadStationing.isStationable(network, road)) continue;
            FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, road);
            if (intent == null) continue;
            double flatElevation = intent.getBaseElevation();
            for (String nodeId : VerticalAlignmentJunctionSynchronizer
                    .junctionStations(network, road).keySet()) {
                RoadNode node = network.getNode(nodeId);
                if (node == null || node.getManualElevation() == null) continue;
                double target = targetElevationWithoutNode(nodeId, flatElevation, intent);
                if (Math.abs(node.getManualElevation() - target) > EPSILON) {
                    conflicts.add(new Conflict(
                        road.getId(), nodeId, flatElevation, node.getManualElevation()));
                }
            }
        }
        return List.copyOf(conflicts);
    }

    public static List<FlatFlatConflict> findFlatFlatConflicts(RoadNetwork network) {
        if (network == null) return List.of();
        List<FlatFlatConflict> conflicts = new ArrayList<>();
        for (RoadNode node : network.getNodes().values()) {
            if (node == null || !node.isJunction() || node.isGradeSeparated()) continue;
            List<FlatRoadAtJunction> flatRoads = flatRoadsAtNode(network, node.getId());
            if (flatRoads.size() < 2) continue;
            Set<Double> distinctBases = new LinkedHashSet<>();
            for (FlatRoadAtJunction entry : flatRoads) {
                distinctBases.add(entry.baseElevation());
            }
            if (distinctBases.size() < 2) continue;
            Double manualElevation = node.getManualElevation();
            if (manualElevation != null) {
                boolean resolved = flatRoads.stream().allMatch(entry -> {
                    Road road = network.getRoad(entry.roadId());
                    FlatVerticalIntent intent = road != null
                        ? FlatVerticalIntentSupport.resolveIntent(network, road) : null;
                    if (intent == null) return false;
                    double effective = effectiveJunctionElevation(
                        node.getId(), entry.baseElevation(), intent, node);
                    return Math.abs(effective - manualElevation) <= EPSILON;
                });
                if (resolved) continue;
            }
            conflicts.add(new FlatFlatConflict(node.getId(), List.copyOf(flatRoads)));
        }
        return List.copyOf(conflicts);
    }

    public static List<TransitionIssue> findTransitionIssues(RoadNetwork network) {
        if (network == null) return List.of();
        List<TransitionIssue> issues = new ArrayList<>();
        for (Road road : network.getRoads().values()) {
            if (road.getVerticalMode() != RoadVerticalMode.FLAT
                    || !RoadStationing.isStationable(network, road)) continue;
            FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, road);
            if (intent == null) continue;
            double maxGrade = road.getMaxSlope() != null ? road.getMaxSlope() : DEFAULT_MAX_GRADE;
            double length = RoadStationing.canonicalLength(network, road);
            RoadVerticalAlignment alignment = FlatProfileCompiler.compile(network, road, intent, maxGrade);
            if (alignment == null) continue;
            for (VerticalProfileDesignRules.Issue issue
                    : VerticalProfileDesignRules.assess(alignment, length, maxGrade)) {
                if (issue.kind() == VerticalProfileDesignRules.IssueKind.GRADE_EXCEEDS_LIMIT
                        || issue.kind() == VerticalProfileDesignRules.IssueKind.GRADE_RUN_TOO_SHORT) {
                    issues.add(new TransitionIssue(
                        road.getId(), issue.fromPviIndex(), issue.toPviIndex(),
                        issue.actual(), issue.limit()));
                }
            }
        }
        return List.copyOf(issues);
    }

    public static boolean hasConflictAt(RoadNetwork network, String roadId, String nodeId) {
        return find(network).stream().anyMatch(conflict ->
            conflict.roadId().equals(roadId) && conflict.nodeId().equals(nodeId))
            || findFlatFlatConflicts(network).stream().anyMatch(conflict ->
                conflict.nodeId().equals(nodeId)
                    && conflict.roads().stream().anyMatch(road -> road.roadId().equals(roadId)));
    }

    /** Applies one shared elevation to every FLAT road at an at-grade junction. */
    public static int applySharedElevationAtJunction(
            RoadNetwork network,
            String nodeId,
            double elevation) {
        return RoadVerticalJunctionService.setAtGradeSharedElevation(
            network, nodeId, elevation, null);
    }

    public static int unifyToRoadBaseAtJunction(
            RoadNetwork network,
            String nodeId,
            String adoptRoadId) {
        if (network == null || nodeId == null || adoptRoadId == null) return 0;
        Road adoptRoad = network.getRoad(adoptRoadId);
        FlatVerticalIntent intent = adoptRoad != null
            ? FlatVerticalIntentSupport.resolveIntent(network, adoptRoad) : null;
        if (intent == null) return 0;
        return applySharedElevationAtJunction(network, nodeId, intent.getBaseElevation());
    }

    public static boolean convertToGradeSeparation(
            RoadNetwork network,
            String nodeId,
            double clearance) {
        if (network == null || nodeId == null) return false;
        return network.setNodeGradeSeparation(nodeId, true, null, clearance);
    }

    /** Makes a flat road follow its junction only when all constrained junctions agree. */
    public static int makeRoadsFlatAtJunctionElevation(RoadNetwork network) {
        List<String> conflictRoadIds = find(network).stream().map(Conflict::roadId).distinct().toList();
        int changed = 0;
        for (String roadId : conflictRoadIds) {
            Road road = network.getRoad(roadId);
            if (road == null) continue;
            if (road.getVerticalMode() != RoadVerticalMode.FLAT
                    || !RoadStationing.isStationable(network, road)) continue;
            Double target = null;
            boolean incompatible = false;
            for (String nodeId : VerticalAlignmentJunctionSynchronizer
                    .junctionStations(network, road).keySet()) {
                RoadNode node = network.getNode(nodeId);
                if (node == null || node.getManualElevation() == null) continue;
                if (target == null) target = node.getManualElevation();
                else if (Math.abs(target - node.getManualElevation()) > EPSILON) incompatible = true;
            }
            if (!incompatible && target != null) {
                FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, road);
                if (intent == null) continue;
                intent.setBaseElevation(target);
                for (String nodeId : intent.getIntersectionOverrides().keySet()) {
                    intent.removeOverride(nodeId);
                }
                FlatVerticalIntentSupport.syncCompiledAlignment(
                    network, road, road.getMaxSlope() != null ? road.getMaxSlope() : DEFAULT_MAX_GRADE);
                changed++;
            }
        }
        return changed;
    }

    /** Writes junction overrides so conflicting flat roads can meet via local transitions. */
    public static int allowConflictingRoadsToSlope(RoadNetwork network) {
        int changed = 0;
        for (Conflict conflict : find(network)) {
            if (applySharedElevationAtJunction(
                    network, conflict.nodeId(), conflict.junctionElevation()) > 0) {
                changed++;
            }
        }
        return changed;
    }

    private static List<FlatRoadAtJunction> flatRoadsAtNode(RoadNetwork network, String nodeId) {
        List<FlatRoadAtJunction> flatRoads = new ArrayList<>();
        for (String roadId : network.getDistinctRoadIdsAtNode(nodeId)) {
            Road road = network.getRoad(roadId);
            if (road == null
                    || road.getVerticalMode() != RoadVerticalMode.FLAT
                    || !RoadStationing.isStationable(network, road)) {
                continue;
            }
            FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, road);
            if (intent == null) continue;
            flatRoads.add(new FlatRoadAtJunction(roadId, intent.getBaseElevation()));
        }
        return flatRoads;
    }

    private static double targetElevationWithoutNode(
            String nodeId,
            double baseElevation,
            FlatVerticalIntent intent) {
        Double override = intent.getIntersectionOverride(nodeId);
        return override != null ? override : baseElevation;
    }

    private static double effectiveJunctionElevation(
            String nodeId,
            double baseElevation,
            FlatVerticalIntent intent,
            RoadNode node) {
        Double override = intent.getIntersectionOverride(nodeId);
        if (override != null) {
            return override;
        }
        if (node != null && node.getManualElevation() != null) {
            return node.getManualElevation();
        }
        return baseElevation;
    }
}
