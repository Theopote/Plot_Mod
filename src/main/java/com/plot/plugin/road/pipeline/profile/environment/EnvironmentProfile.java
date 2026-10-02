package com.plot.plugin.road.pipeline.profile.environment;

import java.util.List;

/** Environment samples along a road profile chainage. */
public record EnvironmentProfile(
        List<EnvironmentSample> samples,
        List<Double> cumulativeDistances) {

    public List<Integer> terrainSamples() {
        return samples.stream().map(EnvironmentSample::terrainY).toList();
    }

    public List<Integer> waterSurfaceSamples() {
        return samples.stream()
            .map(sample -> sample.waterSurfaceY())
            .toList();
    }
}
