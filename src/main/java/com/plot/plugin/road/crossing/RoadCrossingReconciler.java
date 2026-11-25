package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.IntersectionProbeResult;
import com.plot.plugin.road.IntersectionResult;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.profile.VerticalProfileGenerationPolicy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 将几何交叉注册为 {@link RoadCrossing}，不修改拓扑节点/边。 */
public final class RoadCrossingReconciler {
    private RoadCrossingReconciler() {
    }

    /**
     * 探测几何交叉是否均已注册（普通交叉 + 自交）。
     * 歧义 → {@link IntersectionResult#INCOMPLETE}；可 reconcile 的 registry drift →
     * COMPLETE + {@link IntersectionProbeResult#hasPendingWork()}。
     */
    public static IntersectionProbeResult probeRegistryCompleteness(RoadNetwork network) {
        if (network == null || network.getEdges().isEmpty()) {
            return IntersectionProbeResult.resolved();
        }
        CrossingReconcileResult twoRoad = dryRunTwoRoad(network);
        RoadStackCrossingReconciler.StackCrossingReconcileResult stacks =
            RoadStackCrossingReconciler.dryRun(network);
        if (twoRoad.hasAmbiguity() || stacks.ambiguous() > 0
                || twoRoad.result() == IntersectionResult.INCOMPLETE
                || stacks.result() == IntersectionResult.INCOMPLETE) {
            return new IntersectionProbeResult(IntersectionResult.INCOMPLETE, true);
        }
        if (twoRoad.changed() || stacks.changed()) {
            // Repairable drift: keep COMPLETE so hasPendingWork() stays true.
            return new IntersectionProbeResult(IntersectionResult.COMPLETE, true);
        }
        return IntersectionProbeResult.resolved();
    }

    public static IntersectionResult reconcileCrossings(RoadNetwork network) {
        return reconcileCrossingsDetailed(network).result();
    }

    public static CrossingReconcileResult reconcileCrossingsDetailed(RoadNetwork network) {
        if (network == null) {
            return new CrossingReconcileResult(IntersectionResult.COMPLETE, 0, 0, 0, 0);
        }
        List<RoadCrossing> existingCrossings = List.copyOf(network.getCrossings().values());
        List<RoadCrossing> detected = RoadCrossingDetector.detectAll(network);
        Map<String, RoadCrossing> existingById = new HashMap<>();
        for (RoadCrossing crossing : existingCrossings) {
            existingById.put(crossing.id(), crossing);
        }

        List<CrossingMatchAssignment> assignments =
            RoadCrossingMatcher.matchAll(detected, existingCrossings);

        Set<String> matchedExistingIds = new HashSet<>();
        Set<String> ambiguousExistingIds = new HashSet<>();
        int added = 0;
        int updated = 0;
        int ambiguous = 0;

        for (CrossingMatchAssignment assignment : assignments) {
            RoadCrossing detectedCrossing = detected.get(assignment.detectedIndex());
            if (assignment.outcome() == CrossingMatchOutcome.AMBIGUOUS) {
                ambiguous++;
                // Keep all same-pair existing entries — do not delete or recreate.
                String pairKey = RoadCrossingMatcher.roadPairKey(
                    detectedCrossing.roadAId(), detectedCrossing.roadBId());
                for (RoadCrossing existing : existingCrossings) {
                    if (RoadCrossingMatcher.roadPairKey(existing.roadAId(), existing.roadBId())
                            .equals(pairKey)) {
                        ambiguousExistingIds.add(existing.id());
                    }
                }
                continue;
            }
            if (assignment.outcome() == CrossingMatchOutcome.MATCHED) {
                RoadCrossing existing = existingById.get(assignment.existingId());
                RoadCrossing merged = mergeWithExisting(network, detectedCrossing, existing);
                matchedExistingIds.add(existing.id());
                if (geometryChanged(existing, merged)) {
                    updated++;
                }
                network.registerCrossing(merged);
            } else {
                RoadCrossing created = mergeWithExisting(network, detectedCrossing, null);
                added++;
                network.registerCrossing(created);
            }
        }

        int removed = 0;
        for (RoadCrossing existing : existingCrossings) {
            if (matchedExistingIds.contains(existing.id())
                    || ambiguousExistingIds.contains(existing.id())) {
                continue;
            }
            network.removeCrossing(existing.id());
            removed++;
        }

        IntersectionResult result = ambiguous > 0
            ? IntersectionResult.INCOMPLETE
            : IntersectionResult.COMPLETE;
        return new CrossingReconcileResult(result, added, removed, updated, ambiguous);
    }

    /** Probe helper without mutating the network. */
    static CrossingReconcileResult dryRunTwoRoad(RoadNetwork network) {
        List<RoadCrossing> existingCrossings = List.copyOf(network.getCrossings().values());
        List<RoadCrossing> detected = RoadCrossingDetector.detectAll(network);
        List<CrossingMatchAssignment> assignments =
            RoadCrossingMatcher.matchAll(detected, existingCrossings);
        Set<String> matched = new HashSet<>();
        int ambiguous = 0;
        int wouldAdd = 0;
        int wouldUpdate = 0;
        Map<String, RoadCrossing> byId = new HashMap<>();
        for (RoadCrossing crossing : existingCrossings) {
            byId.put(crossing.id(), crossing);
        }
        for (CrossingMatchAssignment assignment : assignments) {
            if (assignment.outcome() == CrossingMatchOutcome.AMBIGUOUS) {
                ambiguous++;
                continue;
            }
            if (assignment.outcome() == CrossingMatchOutcome.MATCHED) {
                matched.add(assignment.existingId());
                RoadCrossing existing = byId.get(assignment.existingId());
                RoadCrossing merged = mergeWithExisting(
                    network, detected.get(assignment.detectedIndex()), existing);
                if (geometryChanged(existing, merged)) {
                    wouldUpdate++;
                }
            } else {
                wouldAdd++;
            }
        }
        int wouldRemove = 0;
        for (RoadCrossing existing : existingCrossings) {
            if (!matched.contains(existing.id())) {
                // Ambiguous pairs are retained; approximate by not counting remove when any ambiguity.
                if (ambiguous == 0) {
                    wouldRemove++;
                }
            }
        }
        IntersectionResult result = ambiguous > 0
            ? IntersectionResult.INCOMPLETE
            : IntersectionResult.COMPLETE;
        boolean pending = wouldAdd > 0 || wouldRemove > 0 || wouldUpdate > 0 || ambiguous > 0;
        if (!pending) {
            return new CrossingReconcileResult(IntersectionResult.COMPLETE, 0, 0, 0, 0);
        }
        return new CrossingReconcileResult(result, wouldAdd, wouldRemove, wouldUpdate, ambiguous);
    }

    private static boolean geometryChanged(RoadCrossing before, RoadCrossing after) {
        return Math.abs(before.stationA() - after.stationA()) > RoadCrossingMatcher.GEOMETRY_CHANGE_EPSILON
            || Math.abs(before.stationB() - after.stationB()) > RoadCrossingMatcher.GEOMETRY_CHANGE_EPSILON
            || positionChanged(before.position(), after.position());
    }

    private static boolean positionChanged(Vec2d before, Vec2d after) {
        if (before == null || after == null) {
            return before != after;
        }
        return Math.abs(before.x - after.x) > RoadCrossingMatcher.GEOMETRY_CHANGE_EPSILON
            || Math.abs(before.y - after.y) > RoadCrossingMatcher.GEOMETRY_CHANGE_EPSILON;
    }

    private static boolean physicalPositionChanged(RoadCrossing before, RoadCrossing after) {
        return positionChanged(before.position(), after.position());
    }

    static RoadCrossing mergeWithExisting(
            RoadNetwork network,
            RoadCrossing detected,
            RoadCrossing existing) {
        if (existing != null) {
            double stationA = stationAligned(detected, existing.roadAId(), existing.stationA());
            double stationB = stationAligned(detected, existing.roadBId(), existing.stationB());
            RoadCrossing refreshed = existing.withRefreshedGeometry(
                stationA,
                stationB,
                detected.position());
            // Station-only / seam remap keeps user-locked or authoritative elevations.
            if (physicalPositionChanged(existing, refreshed)
                    && refreshed.sharedElevation() != null
                    && !VerticalProfileGenerationPolicy.isCrossingSharedElevationAuthoritative(
                        network, refreshed)) {
                return refreshed.withSharedElevation(null);
            }
            return refreshed;
        }
        return new RoadCrossing(
            UUID.randomUUID().toString(),
            detected.roadAId(),
            detected.stationA(),
            detected.roadBId(),
            detected.stationB(),
            detected.position(),
            detected.type(),
            detected.elevatedRoadId(),
            detected.crossingClearance(),
            detected.sharedElevation(),
            detected.cornerRadius());
    }

    private static double stationAligned(RoadCrossing detected, String roadId, double fallback) {
        try {
            return detected.stationOn(roadId);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
