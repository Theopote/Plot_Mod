package com.plot.plugin.road.crossing;

import com.plot.plugin.road.IntersectionProbeResult;
import com.plot.plugin.road.IntersectionResult;
import com.plot.plugin.road.model.RoadNetwork;

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
     * 探测几何交叉是否均已注册为 {@link RoadCrossing}（不修改 live 拓扑）。
     */
    public static IntersectionProbeResult probeRegistryCompleteness(RoadNetwork network) {
        if (network == null || network.getEdges().isEmpty()) {
            return IntersectionProbeResult.resolved();
        }
        Set<String> detectedKeys = new HashSet<>();
        for (RoadCrossing detected : RoadCrossingDetector.detectAll(network)) {
            detectedKeys.add(detected.stableKey());
        }
        Set<String> registeredKeys = new HashSet<>();
        for (RoadCrossing registered : network.getCrossings().values()) {
            registeredKeys.add(registered.stableKey());
        }
        boolean pending = !detectedKeys.equals(registeredKeys);
        return new IntersectionProbeResult(IntersectionResult.COMPLETE, pending);
    }

    public static IntersectionResult reconcileCrossings(RoadNetwork network) {
        if (network == null) {
            return IntersectionResult.COMPLETE;
        }
        Map<String, RoadCrossing> existingByKey = new HashMap<>();
        for (RoadCrossing existing : network.getCrossings().values()) {
            existingByKey.put(existing.stableKey(), existing);
        }

        List<RoadCrossing> detected = RoadCrossingDetector.detectAll(network);
        Set<String> seenIds = new HashSet<>();
        for (RoadCrossing detectedCrossing : detected) {
            RoadCrossing merged = mergeWithExisting(detectedCrossing, existingByKey.get(detectedCrossing.stableKey()));
            seenIds.add(merged.id());
            network.registerCrossing(merged);
        }
        for (RoadCrossing existing : network.getCrossings().values()) {
            if (!seenIds.contains(existing.id())) {
                network.removeCrossing(existing.id());
            }
        }
        return IntersectionResult.COMPLETE;
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
