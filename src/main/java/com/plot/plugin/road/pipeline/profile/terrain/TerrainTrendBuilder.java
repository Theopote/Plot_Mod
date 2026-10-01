package com.plot.plugin.road.pipeline.profile.terrain;

import java.util.ArrayList;
import java.util.List;

/** raw 地形 → median → moving average → 地形趋势线。 */
public final class TerrainTrendBuilder {

    private TerrainTrendBuilder() {
    }

    public static TerrainTrendResult build(
            TerrainProfileSampleChain chain,
            TerrainFollowPreset preset) {
        return build(chain, preset, null, null);
    }

    public static TerrainTrendResult build(
            TerrainProfileSampleChain chain,
            TerrainFollowPreset preset,
            Integer manualStartHeight,
            Integer manualEndHeight) {
        if (chain == null || chain.isEmpty()) {
            return new TerrainTrendResult(List.of(), List.of());
        }
        TerrainFollowPreset effectivePreset = preset != null ? preset : TerrainFollowPreset.STANDARD;

        List<Double> medianFiltered = TerrainProfileFilter.medianFilter(
            chain, effectivePreset.medianWindowMeters());
        TerrainProfileSampleChain medianChain = chain.withElevations(medianFiltered);
        List<Double> trend = TerrainProfileFilter.movingAverage(
            medianChain, effectivePreset.movingAverageWindowMeters());

        trend = applyEndpointOverrides(trend, manualStartHeight, manualEndHeight);
        return new TerrainTrendResult(chain.rawElevations(), trend);
    }

    private static List<Double> applyEndpointOverrides(
            List<Double> trend,
            Integer manualStartHeight,
            Integer manualEndHeight) {
        if (trend.isEmpty()) {
            return trend;
        }
        List<Double> adjusted = new ArrayList<>(trend);
        if (manualStartHeight != null) {
            adjusted.set(0, manualStartHeight.doubleValue());
        }
        if (manualEndHeight != null) {
            adjusted.set(adjusted.size() - 1, manualEndHeight.doubleValue());
        }
        return adjusted;
    }
}
