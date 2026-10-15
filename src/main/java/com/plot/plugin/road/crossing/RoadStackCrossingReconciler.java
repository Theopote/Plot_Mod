package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.IntersectionResult;
import com.plot.plugin.road.model.RoadNetwork;

import java.util.HashSet;
import java.util.List;
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
            return new StackCrossingReconcileResult(IntersectionResult.COMPLETE, 0, 0, 0);
        }
        List<RoadStackCrossing> existing = List.copyOf(network.getStackCrossings().values());
        List<RoadStackCrossing> detected = RoadStackCrossingDetector.detectAll(network);

        Set<String> matchedExistingIds = new HashSet<>();
        Set<String> seenIds = new HashSet<>();
        int added = 0;
        int updated = 0;

        for (RoadStackCrossing detectedStack : detected) {
            RoadStackCrossing existingStack = RoadStackCrossingMatcher.matchExisting(
                detectedStack, existing, matchedExistingIds);
            RoadStackCrossing merged = mergeWithExisting(detectedStack, existingStack);
            if (existingStack == null) {
                added++;
            } else {
                matchedExistingIds.add(existingStack.id());
                if (geometryChanged(existingStack, merged)) {
                    updated++;
                }
            }
            seenIds.add(merged.id());
            network.registerStackCrossing(merged);
        }

        int removed = 0;
        for (RoadStackCrossing prior : existing) {
            if (!seenIds.contains(prior.id())) {
                network.removeStackCrossing(prior.id());
                removed++;
            }
        }
        return new StackCrossingReconcileResult(IntersectionResult.COMPLETE, added, removed, updated);
    }

    public record StackCrossingReconcileResult(
            IntersectionResult result,
            int added,
            int removed,
            int updated) {
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

    private static RoadStackCrossing mergeWithExisting(RoadStackCrossing detected, RoadStackCrossing existing) {
        if (existing != null) {
            return new RoadStackCrossing(
                existing.id(),
                existing.roadId(),
                detected.position(),
                detected.stationA(),
                detected.stationB(),
                existing.type(),
                existing.lowerStation(),
                existing.upperStation(),
                existing.crossingClearance(),
                existing.sharedElevation());
        }
        return new RoadStackCrossing(
            UUID.randomUUID().toString(),
            detected.roadId(),
            detected.position(),
            detected.stationA(),
            detected.stationB(),
            CrossingType.AT_GRADE,
            null,
            null,
            null,
            null);
    }
}
