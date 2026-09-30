package com.plot.plugin.road.crossing;

import com.plot.plugin.road.IntersectionProbeResult;
import com.plot.plugin.road.IntersectionResult;
import com.plot.plugin.road.model.RoadNetwork;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 将几何交叉注册为 {@link RoadCrossing}，不修改拓扑节点/边。 */
public final class RoadCrossingReconciler {
    private RoadCrossingReconciler() {
    }

    /**
     * 探测几何交叉是否均已注册为 {@link RoadCrossing}（不修改 live 拓扑）。
     */
    public static IntersectionProbeResult probeRegistryCompleteness(RoadNetwork network) {
        if (network == null || network.getEdges().isEmpty()) {
            return IntersectionProbeResult.resolved();
        }
        List<RoadCrossing> detected = RoadCrossingDetector.detectAll(network);
        List<RoadCrossing> registered = List.copyOf(network.getCrossings().values());
        if (detected.isEmpty() && registered.isEmpty()) {
            return IntersectionProbeResult.resolved();
        }
        if (detected.size() != registered.size()) {
            return new IntersectionProbeResult(IntersectionResult.COMPLETE, true);
        }
        Set<String> matchedRegisteredIds = new HashSet<>();
        for (RoadCrossing detectedCrossing : detected) {
            RoadCrossing match = RoadCrossingMatcher.matchExisting(
                detectedCrossing, registered, matchedRegisteredIds);
            if (match == null) {
                return new IntersectionProbeResult(IntersectionResult.COMPLETE, true);
            }
            matchedRegisteredIds.add(match.id());
        }
        if (matchedRegisteredIds.size() != registered.size()) {
            return new IntersectionProbeResult(IntersectionResult.COMPLETE, true);
        }
        return IntersectionProbeResult.resolved();
    }

    public static IntersectionResult reconcileCrossings(RoadNetwork network) {
        return reconcileCrossingsDetailed(network).result();
    }

    public static CrossingReconcileResult reconcileCrossingsDetailed(RoadNetwork network) {
        if (network == null) {
            return new CrossingReconcileResult(IntersectionResult.COMPLETE, 0, 0, 0);
        }
        List<RoadCrossing> existingCrossings = List.copyOf(network.getCrossings().values());
        List<RoadCrossing> detected = RoadCrossingDetector.detectAll(network);

        Set<String> matchedExistingIds = new HashSet<>();
        Set<String> seenIds = new HashSet<>();
        int added = 0;
        int updated = 0;

        for (RoadCrossing detectedCrossing : detected) {
            RoadCrossing existing = RoadCrossingMatcher.matchExisting(
                detectedCrossing, existingCrossings, matchedExistingIds);
            RoadCrossing merged = mergeWithExisting(detectedCrossing, existing);
            if (existing == null) {
                added++;
            } else {
                matchedExistingIds.add(existing.id());
                if (geometryChanged(existing, merged)) {
                    updated++;
                }
            }
            seenIds.add(merged.id());
            network.registerCrossing(merged);
        }

        int removed = 0;
        for (RoadCrossing existing : existingCrossings) {
            if (!seenIds.contains(existing.id())) {
                network.removeCrossing(existing.id());
                removed++;
            }
        }
        return new CrossingReconcileResult(IntersectionResult.COMPLETE, added, removed, updated);
    }

    private static boolean geometryChanged(RoadCrossing before, RoadCrossing after) {
        return Math.abs(before.stationA() - after.stationA()) > 1e-6
            || Math.abs(before.stationB() - after.stationB()) > 1e-6
            || !RoadCrossingMatcher.positionsEquivalent(before.position(), after.position());
    }

    private static RoadCrossing mergeWithExisting(RoadCrossing detected, RoadCrossing existing) {
        if (existing != null) {
            return existing.withRefreshedGeometry(
                detected.stationA(),
                detected.stationB(),
                detected.position());
        }
        return new RoadCrossing(
            UUID.randomUUID().toString(),
            detected.roadAId(),
            detected.stationA(),
            detected.roadBId(),
            detected.stationB(),
            detected.position(),
            CrossingType.AT_GRADE,
            null,
            null,
            null);
    }
}
