package com.plot.plugin.road.pipeline.profile.environment;

import java.util.ArrayList;
import java.util.List;

/** Environment samples along a road profile chainage. */
public record EnvironmentProfile(
        List<EnvironmentSample> samples,
        List<Double> cumulativeDistances) {

    private static final double EPSILON = 1e-9;

    public List<Integer> terrainSamples() {
        return samples.stream().map(EnvironmentSample::terrainY).toList();
    }

    public List<Integer> waterSurfaceSamples() {
        return samples.stream()
            .map(EnvironmentSample::waterSurfaceY)
            .toList();
    }

    /**
     * Resample this profile onto solver/ground station chainage.
     */
    public EnvironmentProfile resampleAtStations(List<Double> targetStations) {
        if (targetStations == null || targetStations.isEmpty()) {
            return new EnvironmentProfile(List.of(), List.of());
        }
        if (samples == null || samples.isEmpty()) {
            List<EnvironmentSample> emptySamples = new ArrayList<>(targetStations.size());
            for (double station : targetStations) {
                emptySamples.add(EnvironmentSample.land(station, 0));
            }
            return new EnvironmentProfile(List.copyOf(emptySamples), List.copyOf(targetStations));
        }
        List<EnvironmentSample> resampled = new ArrayList<>(targetStations.size());
        for (double station : targetStations) {
            resampled.add(resampleSample(station));
        }
        return new EnvironmentProfile(List.copyOf(resampled), List.copyOf(targetStations));
    }

    private EnvironmentSample resampleSample(double station) {
        if (station <= samples.getFirst().station() + EPSILON) {
            return samples.getFirst();
        }
        if (station >= samples.getLast().station() - EPSILON) {
            return samples.getLast();
        }
        for (int i = 1; i < samples.size(); i++) {
            EnvironmentSample end = samples.get(i);
            if (station > end.station() + EPSILON) {
                continue;
            }
            EnvironmentSample start = samples.get(i - 1);
            double span = end.station() - start.station();
            if (span <= EPSILON) {
                return mergeSamples(start, end, station);
            }
            double blend = (station - start.station()) / span;
            int terrainY = (int) Math.round(
                start.terrainY() + (end.terrainY() - start.terrainY()) * blend);
            Integer waterY = mergeWaterSurface(start.waterSurfaceY(), end.waterSurfaceY());
            int depth = 0;
            SurfaceContext context = SurfaceContext.LAND;
            if (waterY != null && waterY > terrainY) {
                depth = waterY - terrainY;
                context = depth <= 2 ? SurfaceContext.SHALLOW_WATER : SurfaceContext.DEEP_WATER;
            }
            return new EnvironmentSample(station, terrainY, waterY, depth, context);
        }
        return samples.getLast();
    }

    private static EnvironmentSample mergeSamples(
            EnvironmentSample start,
            EnvironmentSample end,
            double station) {
        Integer waterY = mergeWaterSurface(start.waterSurfaceY(), end.waterSurfaceY());
        int terrainY = Math.max(start.terrainY(), end.terrainY());
        if (waterY != null && waterY > terrainY) {
            int depth = waterY - terrainY;
            SurfaceContext context = depth <= 2 ? SurfaceContext.SHALLOW_WATER : SurfaceContext.DEEP_WATER;
            return new EnvironmentSample(station, terrainY, waterY, depth, context);
        }
        return EnvironmentSample.land(station, terrainY);
    }

    private static Integer mergeWaterSurface(Integer left, Integer right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.max(left, right);
    }
}
