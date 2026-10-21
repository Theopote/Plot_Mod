package com.plot.plugin.road.pipeline.profile.terrain;

import java.util.ArrayList;
import java.util.List;

/** raw 地形 → median → moving average → step transition spread → 地形趋势线。 */
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
        trend = TerrainProfileFilter.spreadStepTransitions(
            chain,
            trend,
            effectivePreset.stepTransitionMeters());

        trend = applyEndpointOverrides(
            trend,
            chain.stations(),
            manualStartHeight,
            manualEndHeight);
        return new TerrainTrendResult(chain.rawElevations(), trend);
    }

    private static final double ENDPOINT_RAMP_MAX_METERS = 40.0;
    private static final double ENDPOINT_RAMP_FRACTION = 0.35;

    private static List<Double> applyEndpointOverrides(
            List<Double> trend,
            List<Double> stations,
            Integer manualStartHeight,
            Integer manualEndHeight) {
        if (trend.isEmpty()) {
            return trend;
        }
        List<Double> adjusted = new ArrayList<>(trend);
        double totalLength = stations.isEmpty() ? 0.0 : stations.getLast();
        double rampLength = Math.min(
            ENDPOINT_RAMP_MAX_METERS,
            Math.max(8.0, totalLength * ENDPOINT_RAMP_FRACTION));
        if (manualStartHeight != null) {
            rampTowardStart(adjusted, stations, manualStartHeight.doubleValue(), rampLength);
        }
        if (manualEndHeight != null) {
            rampTowardEnd(adjusted, stations, manualEndHeight.doubleValue(), rampLength);
        }
        return adjusted;
    }

    private static void rampTowardStart(
            List<Double> trend,
            List<Double> stations,
            double targetHeight,
            double rampLength) {
        trend.set(0, targetHeight);
        if (stations.size() != trend.size() || rampLength <= 0.0) {
            return;
        }
        for (int i = 1; i < trend.size(); i++) {
            double station = stations.get(i);
            if (station >= rampLength) {
                break;
            }
            double blend = station / rampLength;
            trend.set(i, trend.get(i) * (1.0 - blend) + targetHeight * blend);
        }
    }

    private static void rampTowardEnd(
            List<Double> trend,
            List<Double> stations,
            double targetHeight,
            double rampLength) {
        int last = trend.size() - 1;
        trend.set(last, targetHeight);
        if (stations.size() != trend.size() || rampLength <= 0.0) {
            return;
        }
        double totalLength = stations.get(last);
        double rampStart = Math.max(0.0, totalLength - rampLength);
        for (int i = last - 1; i >= 0; i--) {
            double station = stations.get(i);
            if (station <= rampStart) {
                break;
            }
            double blend = (totalLength - station) / rampLength;
            trend.set(i, trend.get(i) * (1.0 - blend) + targetHeight * blend);
        }
    }
}
