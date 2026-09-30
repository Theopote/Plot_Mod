package com.plot.plugin.road.crossing;

import com.plot.plugin.road.IntersectionResult;
import com.plot.plugin.road.model.RoadNetwork;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 将几何交叉注册为 {@link RoadCrossing}，不修改拓扑节点/边。 */
public final class RoadCrossingReconciler {
    private RoadCrossingReconciler() {
    }

    public static IntersectionResult reconcileCrossings(RoadNetwork network) {
        if (network == null) {
            return IntersectionResult.COMPLETE;
        }
        List<RoadCrossing> detected = RoadCrossingDetector.detectAll(network);
        Set<String> seen = new HashSet<>();
        for (RoadCrossing crossing : detected) {
            seen.add(crossing.id());
            network.registerCrossing(crossing);
        }
        for (RoadCrossing existing : network.getCrossings().values()) {
            if (!seen.contains(existing.id())) {
                network.removeCrossing(existing.id());
            }
        }
        return IntersectionResult.COMPLETE;
    }
}
