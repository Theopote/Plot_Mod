package com.plot.plugin.road.pipeline.profile.terrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 沿桩号距离窗口的地形高程滤波（median / moving average / step transition）。 */
public final class TerrainProfileFilter {

    private static final double EPSILON = 1e-9;
    /** Reference grade for slope-aware step spreading when maxSlope is unavailable here. */
    private static final double REFERENCE_GRADE_PERCENT = 8.0;
    /** Ignore single-block noise; only spread persistent terrain steps. */
    private static final double DEFAULT_STEP_THRESHOLD_BLOCKS = 2.0;
    private static final int MIN_PLATEAU_SAMPLES = 3;
    private static final double MAX_ROAD_FRACTION_FOR_STEP_SPREAD = 0.85;

    private TerrainProfileFilter() {
    }

    public static List<Double> medianFilter(TerrainProfileSampleChain chain, double windowMeters) {
        return filter(chain, windowMeters, TerrainProfileFilter::median);
    }

    public static List<Double> movingAverage(TerrainProfileSampleChain chain, double windowMeters) {
        return filter(chain, windowMeters, values -> {
            double sum = 0.0;
            for (double value : values) {
                sum += value;
            }
            return sum / values.length;
        });
    }

    /**
     * 将 raw 地形中的块级台阶在 trend 上展开为有限长度的线性过渡，避免趋势线在台阶处仍保留尖角。
     * 过渡长度会随高差自动拉长，保证趋势坡度不超过参考纵坡。
     */
    public static List<Double> spreadStepTransitions(
            TerrainProfileSampleChain chain,
            List<Double> trend,
            double transitionMeters) {
        return spreadStepTransitions(
            chain,
            trend,
            transitionMeters,
            DEFAULT_STEP_THRESHOLD_BLOCKS);
    }

    public static List<Double> spreadStepTransitions(
            TerrainProfileSampleChain chain,
            List<Double> trend,
            double transitionMeters,
            double stepThresholdBlocks) {
        if (chain == null || chain.isEmpty() || trend == null || trend.isEmpty()) {
            return trend != null ? List.copyOf(trend) : List.of();
        }
        if (transitionMeters <= EPSILON || chain.size() < 2) {
            return List.copyOf(trend);
        }
        List<Double> stations = chain.stations();
        List<Double> raw = chain.rawElevations();
        if (stations.size() != trend.size() || raw.size() != trend.size()) {
            throw new IllegalArgumentException("chain and trend must have equal length");
        }

        List<Double> adjusted = new ArrayList<>(trend);
        double roadLength = chain.totalLength();
        if (roadLength <= EPSILON) {
            return adjusted;
        }

        for (int i = 1; i < raw.size(); i++) {
            if (Math.abs(raw.get(i) - raw.get(i - 1)) + EPSILON < stepThresholdBlocks) {
                continue;
            }
            if (!isPlateauStep(raw, i, stepThresholdBlocks)) {
                continue;
            }
            double stepStation = (stations.get(i) + stations.get(i - 1)) * 0.5;
            double plateauWindow = Math.max(transitionMeters * 0.5, averageSpacing(chain));
            double lowLevel = plateauAverage(stations, raw, stepStation - plateauWindow * 2.0, stepStation);
            double highLevel = plateauAverage(stations, raw, stepStation, stepStation + plateauWindow * 2.0);
            if (lowLevel > highLevel) {
                double swap = lowLevel;
                lowLevel = highLevel;
                highLevel = swap;
            }
            double elevationDelta = highLevel - lowLevel;
            if (elevationDelta <= EPSILON) {
                continue;
            }
            double slopeLimitedLength = elevationDelta / (REFERENCE_GRADE_PERCENT / 100.0);
            double effectiveTransition = Math.max(transitionMeters, slopeLimitedLength);
            if (effectiveTransition > roadLength * MAX_ROAD_FRACTION_FOR_STEP_SPREAD) {
                continue;
            }
            effectiveTransition = Math.min(effectiveTransition, roadLength);
            double transitionStart = stepStation - effectiveTransition * 0.5;
            double transitionEnd = stepStation + effectiveTransition * 0.5;
            if (transitionStart < stations.getFirst()) {
                transitionEnd += stations.getFirst() - transitionStart;
                transitionStart = stations.getFirst();
            }
            if (transitionEnd > stations.getLast()) {
                transitionStart -= transitionEnd - stations.getLast();
                transitionEnd = stations.getLast();
            }
            transitionStart = Math.max(transitionStart, stations.getFirst());
            transitionEnd = Math.min(transitionEnd, stations.getLast());
            if (transitionEnd - transitionStart <= EPSILON) {
                continue;
            }
            for (int j = 0; j < stations.size(); j++) {
                double station = stations.get(j);
                if (station + EPSILON < transitionStart || station - EPSILON > transitionEnd) {
                    continue;
                }
                double blend = (station - transitionStart) / (transitionEnd - transitionStart);
                adjusted.set(j, lowLevel + elevationDelta * blend);
            }
        }
        return adjusted;
    }

    private static boolean isPlateauStep(List<Double> raw, int stepIndex, double stepThresholdBlocks) {
        double stepDelta = Math.abs(raw.get(stepIndex) - raw.get(stepIndex - 1));
        if (stepDelta + EPSILON < stepThresholdBlocks) {
            return false;
        }
        int leftPlateau = countPlateauRun(raw, stepIndex - 1, -1);
        int rightPlateau = countPlateauRun(raw, stepIndex, 1);
        return leftPlateau >= MIN_PLATEAU_SAMPLES && rightPlateau >= MIN_PLATEAU_SAMPLES;
    }

    private static int countPlateauRun(List<Double> raw, int startIndex, int direction) {
        if (startIndex < 0 || startIndex >= raw.size()) {
            return 0;
        }
        double level = raw.get(startIndex);
        int count = 1;
        for (int i = startIndex + direction; i >= 0 && i < raw.size(); i += direction) {
            if (Math.abs(raw.get(i) - level) > EPSILON) {
                break;
            }
            count++;
        }
        return count;
    }

    private static List<Double> filter(
            TerrainProfileSampleChain chain,
            double windowMeters,
            java.util.function.ToDoubleFunction<double[]> reducer) {
        if (chain.isEmpty()) {
            return List.of();
        }
        if (windowMeters <= 0.0) {
            return List.copyOf(chain.rawElevations());
        }
        double halfWindow = halfWindowMeters(chain, windowMeters);
        List<Double> stations = chain.stations();
        List<Double> elevations = chain.rawElevations();
        List<Double> filtered = new ArrayList<>(elevations.size());
        for (int i = 0; i < elevations.size(); i++) {
            double[] windowValues = collectDistanceWindow(stations, elevations, i, halfWindow);
            filtered.add(reducer.applyAsDouble(windowValues));
        }
        return filtered;
    }

    /**
     * 将米制窗口换算为两侧索引半径；保留供测试与近似估算。
     */
    static int indexRadiusForWindow(TerrainProfileSampleChain chain, double windowMeters) {
        if (chain.size() <= 1) {
            return 0;
        }
        double averageSpacing = averageSpacing(chain);
        if (averageSpacing <= EPSILON) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(windowMeters / (2.0 * averageSpacing)));
    }

    private static double halfWindowMeters(TerrainProfileSampleChain chain, double windowMeters) {
        return indexRadiusForWindow(chain, windowMeters) * averageSpacing(chain);
    }

    private static double averageSpacing(TerrainProfileSampleChain chain) {
        if (chain.size() <= 1) {
            return 0.0;
        }
        return chain.totalLength() / (chain.size() - 1);
    }

    private static double[] collectDistanceWindow(
            List<Double> stations,
            List<Double> elevations,
            int centerIndex,
            double halfWindowMeters) {
        double centerStation = stations.get(centerIndex);
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < stations.size(); i++) {
            if (Math.abs(stations.get(i) - centerStation) <= halfWindowMeters + EPSILON) {
                values.add(elevations.get(i));
            }
        }
        if (values.isEmpty()) {
            return new double[] {elevations.get(centerIndex)};
        }
        double[] window = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            window[i] = values.get(i);
        }
        return window;
    }

    private static double plateauAverage(
            List<Double> stations,
            List<Double> elevations,
            double startStation,
            double endStation) {
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < stations.size(); i++) {
            double station = stations.get(i);
            if (station + EPSILON < startStation || station - EPSILON > endStation) {
                continue;
            }
            sum += elevations.get(i);
            count++;
        }
        if (count == 0) {
            return elevations.getFirst();
        }
        return sum / count;
    }

    private static double median(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        double[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        if (sorted.length % 2 == 0) {
            return (sorted[mid - 1] + sorted[mid]) * 0.5;
        }
        return sorted[mid];
    }
}
