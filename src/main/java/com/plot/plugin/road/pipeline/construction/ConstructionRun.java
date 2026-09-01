package com.plot.plugin.road.pipeline.construction;

import com.plot.plugin.road.RoadConstructionType;

/** One contiguous finalized construction interval along a sampled road profile. */
public record ConstructionRun(
        RoadConstructionType type,
        int startSegmentIndex,
        int endSegmentIndexExclusive,
        double startStation,
        double endStation,
        int maximumHeightDifference,
        double averageHeightDifference) {

    public double length() {
        return Math.max(0.0, endStation - startStation);
    }
}
