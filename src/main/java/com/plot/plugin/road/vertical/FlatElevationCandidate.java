package com.plot.plugin.road.vertical;

/** One evaluated horizontal elevation candidate for a flat road. */
public record FlatElevationCandidate(
        int elevation,
        double score,
        int estimatedCutVolume,
        int estimatedFillVolume,
        double estimatedBridgeLength,
        double estimatedTunnelLength,
        int estimatedEarthworkBlocks,
        boolean feasible) {

    public static FlatElevationCandidate infeasible(int elevation) {
        return new FlatElevationCandidate(
            elevation,
            Double.POSITIVE_INFINITY,
            0,
            0,
            0.0,
            0.0,
            0,
            false);
    }
}
