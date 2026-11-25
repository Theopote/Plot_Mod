package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.IntersectionResult;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.profile.VerticalProfileGenerationPolicy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 将几何自交注册为 {@link RoadStackCrossing}，不修改拓扑节点/边。 */
public final class RoadStackCrossingReconciler {
    private RoadStackCrossingReconciler() {
    }

    public static IntersectionResult reconcileStackCrossings(RoadNetwork network) {
        return reconcileStackCrossingsDetailed(network).result();
    }

    public static StackCrossingReconcileResult reconcileStackCrossingsDetailed(RoadNetwork network) {
        if (network == null) {
            return new StackCrossingReconcileResult(IntersectionResult.COMPLETE, 0, 0, 0, 0);
        }
        List<RoadStackCrossing> existing = List.copyOf(network.getStackCrossings().values());
        List<RoadStackCrossing> detected = RoadStackCrossingDetector.detectAll(network);
        Map<String, RoadStackCrossing> byId = new HashMap<>();
        for (RoadStackCrossing stack : existing) {
            byId.put(stack.id(), stack);
        }

        List<CrossingMatchAssignment> assignments =
            RoadStackCrossingMatcher.matchAll(detected, existing);

        Set<String> matchedExistingIds = new HashSet<>();
        Set<String> ambiguousExistingIds = new HashSet<>();
        int added = 0;
        int updated = 0;
        int ambiguous = 0;

        for (CrossingMatchAssignment assignment : assignments) {
            RoadStackCrossing detectedStack = detected.get(assignment.detectedIndex());
            if (assignment.outcome() == CrossingMatchOutcome.AMBIGUOUS) {
                ambiguous++;
                for (RoadStackCrossing prior : existing) {
                    if (detectedStack.roadId().equals(prior.roadId())) {
                        ambiguousExistingIds.add(prior.id());
                    }
                }
                continue;
            }
            if (assignment.outcome() == CrossingMatchOutcome.MATCHED) {
                RoadStackCrossing existingStack = byId.get(assignment.existingId());
                RoadStackCrossing merged = mergeWithExisting(network, detectedStack, existingStack);
                matchedExistingIds.add(existingStack.id());
                if (geometryChanged(existingStack, merged)) {
                    updated++;
                }
                network.registerStackCrossing(merged);
            } else {
                added++;
                network.registerStackCrossing(mergeWithExisting(network, detectedStack, null));
            }
        }

        int removed = 0;
        for (RoadStackCrossing prior : existing) {
            if (matchedExistingIds.contains(prior.id()) || ambiguousExistingIds.contains(prior.id())) {
                continue;
            }
            network.removeStackCrossing(prior.id());
            removed++;
        }

        IntersectionResult result = ambiguous > 0
            ? IntersectionResult.INCOMPLETE
            : IntersectionResult.COMPLETE;
        return new StackCrossingReconcileResult(result, added, removed, updated, ambiguous);
    }

    static StackCrossingReconcileResult dryRun(RoadNetwork network) {
        List<RoadStackCrossing> existing = List.copyOf(network.getStackCrossings().values());
        List<RoadStackCrossing> detected = RoadStackCrossingDetector.detectAll(network);
        List<CrossingMatchAssignment> assignments =
            RoadStackCrossingMatcher.matchAll(detected, existing);
        Set<String> matched = new HashSet<>();
        int ambiguous = 0;
        int wouldAdd = 0;
        int wouldUpdate = 0;
        Map<String, RoadStackCrossing> byId = new HashMap<>();
        for (RoadStackCrossing stack : existing) {
            byId.put(stack.id(), stack);
        }
        for (CrossingMatchAssignment assignment : assignments) {
            if (assignment.outcome() == CrossingMatchOutcome.AMBIGUOUS) {
                ambiguous++;
                continue;
            }
            if (assignment.outcome() == CrossingMatchOutcome.MATCHED) {
                matched.add(assignment.existingId());
                RoadStackCrossing existingStack = byId.get(assignment.existingId());
                RoadStackCrossing merged = mergeWithExisting(
                    network, detected.get(assignment.detectedIndex()), existingStack);
                if (geometryChanged(existingStack, merged)) {
                    wouldUpdate++;
                }
            } else {
                wouldAdd++;
            }
        }
        int wouldRemove = 0;
        if (ambiguous == 0) {
            for (RoadStackCrossing prior : existing) {
                if (!matched.contains(prior.id())) {
                    wouldRemove++;
                }
            }
        }
        boolean pending = wouldAdd > 0 || wouldRemove > 0 || wouldUpdate > 0 || ambiguous > 0;
        IntersectionResult result = ambiguous > 0
            ? IntersectionResult.INCOMPLETE
            : IntersectionResult.COMPLETE;
        if (!pending) {
            return new StackCrossingReconcileResult(IntersectionResult.COMPLETE, 0, 0, 0, 0);
        }
        return new StackCrossingReconcileResult(result, wouldAdd, wouldRemove, wouldUpdate, ambiguous);
    }

    public record StackCrossingReconcileResult(
            IntersectionResult result,
            int added,
            int removed,
            int updated,
            int ambiguous) {

        public StackCrossingReconcileResult(
                IntersectionResult result,
                int added,
                int removed,
                int updated) {
            this(result, added, removed, updated, 0);
        }

        public boolean changed() {
            return added > 0 || removed > 0 || updated > 0;
        }

        public boolean hasAmbiguity() {
            return ambiguous > 0;
        }
    }

    private static boolean geometryChanged(RoadStackCrossing before, RoadStackCrossing after) {
        return Math.abs(before.stationA() - after.stationA()) > RoadStackCrossingMatcher.GEOMETRY_CHANGE_EPSILON
            || Math.abs(before.stationB() - after.stationB()) > RoadStackCrossingMatcher.GEOMETRY_CHANGE_EPSILON
            || positionChanged(before.position(), after.position());
    }

    private static boolean positionChanged(Vec2d before, Vec2d after) {
        if (before == null || after == null) {
            return before != after;
        }
        return Math.abs(before.x - after.x) > RoadStackCrossingMatcher.GEOMETRY_CHANGE_EPSILON
            || Math.abs(before.y - after.y) > RoadStackCrossingMatcher.GEOMETRY_CHANGE_EPSILON;
    }

    private static RoadStackCrossing mergeWithExisting(
            RoadNetwork network,
            RoadStackCrossing detected,
            RoadStackCrossing existing) {
        if (existing != null) {
            RoadStackCrossing refreshed = existing.withRefreshedGeometry(
                detected.stationA(), detected.stationB(), detected.position());
            if (positionChanged(existing.position(), refreshed.position())
                    && refreshed.sharedElevation() != null
                    && !VerticalProfileGenerationPolicy.isStackCrossingSharedElevationAuthoritative(
                        network, refreshed)) {
                return refreshed.withSharedElevation(null);
            }
            return refreshed;
        }
        return new RoadStackCrossing(
            UUID.randomUUID().toString(),
            detected.roadId(),
            detected.position(),
            detected.stationA(),
            detected.stationB(),
            detected.type(),
            detected.lowerStation(),
            detected.upperStation(),
            detected.crossingClearance(),
            detected.sharedElevation());
    }
}
