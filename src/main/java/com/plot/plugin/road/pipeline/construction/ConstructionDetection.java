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
        List<Double> segmentDistances) {
}
