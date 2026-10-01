package com.plot.plugin.road.pipeline.profile.terrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 沿桩号距离窗口的地形高程滤波（median / moving average）。 */
public final class TerrainProfileFilter {

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
        int indexRadius = indexRadiusForWindow(chain, windowMeters);
        List<Double> elevations = chain.rawElevations();
        List<Double> filtered = new ArrayList<>(elevations.size());
        for (int i = 0; i < elevations.size(); i++) {
            double[] windowValues = collectIndexWindow(elevations, i, indexRadius);
            filtered.add(reducer.applyAsDouble(windowValues));
        }
        return filtered;
    }

    /**
     * 将米制窗口换算为两侧索引半径，保证典型 10 m 采样间距下 median 能覆盖邻点。
     */
    static int indexRadiusForWindow(TerrainProfileSampleChain chain, double windowMeters) {
        if (chain.size() <= 1) {
            return 0;
        }
        double averageSpacing = chain.totalLength() / (chain.size() - 1);
        if (averageSpacing <= 1e-9) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(windowMeters / (2.0 * averageSpacing)));
    }

    private static double[] collectIndexWindow(List<Double> elevations, int centerIndex, int radius) {
        int start = Math.max(0, centerIndex - radius);
        int end = Math.min(elevations.size() - 1, centerIndex + radius);
        double[] values = new double[end - start + 1];
        for (int i = start; i <= end; i++) {
            values[i - start] = elevations.get(i);
        }
        return values;
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
