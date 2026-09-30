package com.plot.plugin.road.crossing;

import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.IntersectionResult;
import com.plot.plugin.road.model.RoadNetwork;

/**
 * 在 snapshot / 生成前将 {@link RoadCrossing} 临时物化为共享节点拓扑，兼容旧 junction 管线。
 */
public final class RoadCrossingMaterializer {
    private RoadCrossingMaterializer() {
    }

    public static RoadNetwork materializeForSnapshot(RoadNetwork source) {
        if (source == null) {
            return new RoadNetwork();
        }
        if (source.getCrossings().isEmpty()) {
            return source.snapshot();
        }
        RoadNetwork snapshot = source.snapshot();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        builder.detectAndSplitIntersections(snapshot);
        return snapshot;
    }

    public static IntersectionResult materializeInPlace(RoadNetwork network) {
        if (network == null || network.getCrossings().isEmpty()) {
            return IntersectionResult.COMPLETE;
        }
        return new RoadNetworkBuilder().detectAndSplitIntersections(network);
    }
}
