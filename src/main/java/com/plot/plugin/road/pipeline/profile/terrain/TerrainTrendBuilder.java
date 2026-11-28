package com.plot.plugin.road.pipeline.profile.terrain;

import java.util.ArrayList;
import java.util.List;

/** raw 地形 → median → moving average → step transition spread → 地形趋势线。 */
public final class TerrainTrendBuilder {

    private static final double ENDPOINT_RAMP_MAX_METERS = 40.0;
    private static final double ENDPOINT_RAMP_FRACTION = 0.35;
    private static final double DEFAULT_REFERENCE_GRADE_PERCENT = 8.0;

    private TerrainTrendBuilder() {
    }

    public static TerrainTrendResult build(
            TerrainProfileSampleChain chain,
            TerrainFollowPreset preset) {
        return build(chain, preset, null, null, DEFAULT_REFERENCE_GRADE_PERCENT);
    }

    public static TerrainTrendResult build(
            TerrainProfileSampleChain chain,
            TerrainFollowPreset preset,
            Integer manualStartHeight,
            Integer manualEndHeight) {
        return build(chain, preset, manualStartHeight, manualEndHeight, DEFAULT_REFERENCE_GRADE_PERCENT);
    }

    public static TerrainTrendResult build(
            TerrainProfileSampleChain chain,
            TerrainFollowPreset preset,
            Integer manualStartHeight,
            Integer manualEndHeight,
            double referenceGradePercent) {
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
            effectivePreset.stepTransitionMeters(),
            referenceGradePercent > 0.0 ? referenceGradePercent : DEFAULT_REFERENCE_GRADE_PERCENT);

        trend = applyEndpointOverrides(
            trend,
            chain.stations(),
            manualStartHeight,
            manualEndHeight);
        return new TerrainTrendResult(chain.rawElevations(), trend);
    }

    private static List<Double> applyEndpointOverrides(
            List<Double> trend,
            List<Double> stations,
            Integer manualStartHeight,
            Integer manualEndHeight) {
        if (trend.isEmpty()) {
            return trend;
        }
        if (manualStartHeight == null && manualEndHeight == null) {
            return trend;
        }
        List<Double> adjusted = new ArrayList<>(trend);
        double totalLength = stations.isEmpty() ? 0.0 : stations.getLast();
        double rampLength = Math.min(
            ENDPOINT_RAMP_MAX_METERS,
            Math.max(8.0, totalLength * ENDPOINT_RAMP_FRACTION));
        if (stations.size() != adjusted.size() || rampLength <= 0.0 || totalLength <= 0.0) {
            if (manualStartHeight != null) {
                adjusted.set(0, manualStartHeight.doubleValue());
            }
            if (manualEndHeight != null) {
                adjusted.set(adjusted.size() - 1, manualEndHeight.doubleValue());
            }
            return adjusted;
        }

        // Unified pass: near a locked end use target; away restore terrain. Overlapping
        // start/end ramps combine both endpoint weights instead of sequential overwrite.
        for (int i = 0; i < adjusted.size(); i++) {
            double station = stations.get(i);
            double terrain = adjusted.get(i);
            double startWeight = 0.0;
            double endWeight = 0.0;
            if (manualStartHeight != null && station <= rampLength) {
                startWeight = 1.0 - (station / rampLength);
            }
            if (manualEndHeight != null) {
                double distanceFromEnd = totalLength - station;
                if (distanceFromEnd <= rampLength) {
                    endWeight = 1.0 - (distanceFromEnd / rampLength);
                }
            }
            double lockedWeight = startWeight + endWeight;
            if (lockedWeight <= 1e-12) {
                continue;
            }
            double target;
            if (manualStartHeight != null && manualEndHeight != null && startWeight > 0.0 && endWeight > 0.0) {
                target = (manualStartHeight * startWeight + manualEndHeight * endWeight) / lockedWeight;
            } else if (manualStartHeight != null && startWeight > 0.0) {
                target = manualStartHeight.doubleValue();
            } else {
                target = manualEndHeight.doubleValue();
            }
            double t = Math.min(1.0, lockedWeight);
            // Y = target*(1-t_terrain) + terrain*t_terrain, with influence strongest at ends.
            // Here lockedWeight is the target influence (1 at endpoint, 0 at ramp edge).
            adjusted.set(i, target * t + terrain * (1.0 - t));
        }
        if (manualStartHeight != null) {
            adjusted.set(0, manualStartHeight.doubleValue());
        }
        if (manualEndHeight != null) {
            adjusted.set(adjusted.size() - 1, manualEndHeight.doubleValue());
        }
        return adjusted;
    }
}
