package com.plot.plugin.road.pipeline.construction;

import com.plot.plugin.road.RoadConstructionType;

import java.util.List;

/**
 * Construction classification for each sampled segment (Normal / Fill / Cut / Bridge / Tunnel).
 */
public record ConstructionDetection(
        List<BridgeSegment> bridges,
        List<TunnelSegment> tunnels,
        List<RoadConstructionType> constructionTypes,
        List<Double> segmentDistances,
        List<ConstructionRun> runs) {

    public ConstructionDetection(
            List<BridgeSegment> bridges,
            List<TunnelSegment> tunnels,
            List<RoadConstructionType> constructionTypes,
            List<Double> segmentDistances) {
        this(bridges, tunnels, constructionTypes, segmentDistances, List.of());
    }

    public ConstructionDetection {
        bridges = List.copyOf(bridges);
        tunnels = List.copyOf(tunnels);
        constructionTypes = List.copyOf(constructionTypes);
        segmentDistances = List.copyOf(segmentDistances);
        runs = List.copyOf(runs);
    }

    public long runCount(RoadConstructionType type) {
        return runs.stream().filter(run -> run.type() == type).count();
    }
}
