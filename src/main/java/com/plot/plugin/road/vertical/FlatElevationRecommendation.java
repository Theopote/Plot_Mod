package com.plot.plugin.road.vertical;

import java.util.List;

/** Derived analysis for choosing a flat-road baseline elevation; not persisted until adopted. */
public record FlatElevationRecommendation(
        FlatElevationCandidate best,
        List<FlatElevationCandidate> alternatives,
        int terrainSampleCount) {

    public static FlatElevationRecommendation empty() {
        return new FlatElevationRecommendation(
            FlatElevationCandidate.infeasible(64),
            List.of(),
            0);
    }

    public boolean hasRecommendation() {
        return terrainSampleCount > 0 && best != null && best.feasible();
    }
}
