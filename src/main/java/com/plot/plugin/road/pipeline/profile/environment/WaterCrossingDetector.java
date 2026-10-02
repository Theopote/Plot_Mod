package com.plot.plugin.road.pipeline.profile.environment;

import java.util.ArrayList;
import java.util.List;

/** Detects continuous exposed-water spans along an {@link EnvironmentProfile}. */
public final class WaterCrossingDetector {

    private static final double EPSILON = 1e-9;

    private WaterCrossingDetector() {
    }

    public static List<WaterCrossing> detect(EnvironmentProfile profile) {
        if (profile == null || profile.samples().isEmpty()) {
            return List.of();
        }
        List<EnvironmentSample> samples = profile.samples();
        List<WaterCrossing> crossings = new ArrayList<>();
        int index = 0;
        while (index < samples.size()) {
            if (!samples.get(index).hasWater()) {
                index++;
                continue;
            }
            int start = index;
            while (index < samples.size() && samples.get(index).hasWater()) {
                index++;
            }
            int end = index - 1;
            crossings.add(buildCrossing(samples, start, end));
        }
        return List.copyOf(crossings);
    }

    private static WaterCrossing buildCrossing(
            List<EnvironmentSample> samples,
            int startIndex,
            int endIndex) {
        double firstWaterSample = samples.get(startIndex).station();
        double lastWaterSample = samples.get(endIndex).station();
        double estimatedStart = estimateCrossingStart(samples, startIndex);
        double estimatedEnd = estimateCrossingEnd(samples, endIndex);
        int entryBank = bankTerrainY(samples, startIndex, -1);
        int exitBank = bankTerrainY(samples, endIndex, 1);
        int waterSurface = representativeWaterSurface(samples, startIndex, endIndex);
        double totalDepth = 0.0;
        double maxDepth = 0.0;
        int count = 0;
        for (int i = startIndex; i <= endIndex; i++) {
            EnvironmentSample sample = samples.get(i);
            if (!sample.hasWater()) {
                continue;
            }
            totalDepth += sample.waterDepth();
            maxDepth = Math.max(maxDepth, sample.waterDepth());
            count++;
        }
        double averageDepth = count > 0 ? totalDepth / count : 0.0;
        double lengthMeters = Math.max(0.0, estimatedEnd - estimatedStart);
        return new WaterCrossing(
            estimatedStart,
            estimatedStart,
            estimatedEnd,
            estimatedEnd,
            firstWaterSample,
            lastWaterSample,
            lengthMeters,
            averageDepth,
            maxDepth,
            entryBank,
            exitBank,
            waterSurface,
            WaterCrossingStrategy.BRIDGE);
    }

    static double estimateCrossingStart(List<EnvironmentSample> samples, int firstWaterIndex) {
        EnvironmentSample firstWater = samples.get(firstWaterIndex);
        if (firstWaterIndex <= 0) {
            return firstWater.station();
        }
        EnvironmentSample previousLand = samples.get(firstWaterIndex - 1);
        return midpoint(previousLand.station(), firstWater.station());
    }

    static double estimateCrossingEnd(List<EnvironmentSample> samples, int lastWaterIndex) {
        EnvironmentSample lastWater = samples.get(lastWaterIndex);
        if (lastWaterIndex >= samples.size() - 1) {
            return lastWater.station();
        }
        EnvironmentSample nextLand = samples.get(lastWaterIndex + 1);
        return midpoint(lastWater.station(), nextLand.station());
    }

    private static double midpoint(double left, double right) {
        return (left + right) * 0.5;
    }

    private static int bankTerrainY(List<EnvironmentSample> samples, int waterIndex, int direction) {
        int index = waterIndex + direction;
        while (index >= 0 && index < samples.size()) {
            EnvironmentSample sample = samples.get(index);
            if (!sample.hasWater()) {
                return sample.terrainY();
            }
            index += direction;
        }
        return samples.get(waterIndex).terrainY();
    }

    private static int representativeWaterSurface(
            List<EnvironmentSample> samples,
            int startIndex,
            int endIndex) {
        int max = Integer.MIN_VALUE;
        for (int i = startIndex; i <= endIndex; i++) {
            Integer water = samples.get(i).waterSurfaceY();
            if (water != null) {
                max = Math.max(max, water);
            }
        }
        return max == Integer.MIN_VALUE ? samples.get(startIndex).terrainY() : max;
    }
}
