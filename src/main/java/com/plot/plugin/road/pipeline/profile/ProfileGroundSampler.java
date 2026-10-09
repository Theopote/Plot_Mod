package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.core.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * 沿路径采样地面高程，供纵坡求解与设计纵断面预览共用。
 */
public final class ProfileGroundSampler {

    public record SampleData(
            List<Integer> groundSamples,
            List<Double> cumulativeDistances,
            List<Integer> groundStarts,
            List<Integer> groundEnds) {
    }

    private ProfileGroundSampler() {
    }

    public static SampleData collect(
            List<PathSegment> segments,
            TerrainSampler terrain,
            double halfWidth) {
        List<Integer> groundSamples = new ArrayList<>();
        List<Double> cumulativeDistances = new ArrayList<>();
        List<Integer> groundStarts = new ArrayList<>();
        List<Integer> groundEnds = new ArrayList<>();
        double accumulatedDistance = 0.0;

        List<OptionalInt> stationSamples = new ArrayList<>();
        for (PathSegment segment : segments) {
            Vec2d tangent = segment.end.subtract(segment.start);
            stationSamples.add(terrain.sampleLoadedCrossSectionGroundY(segment.start, tangent, halfWidth));
            cumulativeDistances.add(accumulatedDistance);
            accumulatedDistance += segment.distance;
        }

        if (!segments.isEmpty()) {
            PathSegment last = segments.getLast();
            Vec2d tangent = last.end.subtract(last.start);
            stationSamples.add(terrain.sampleLoadedCrossSectionGroundY(last.end, tangent, halfWidth));
            cumulativeDistances.add(accumulatedDistance);
        }

        List<Integer> filled = fillLoadedGaps(stationSamples);
        for (int i = 0; i < segments.size(); i++) {
            int groundStart = filled.get(i);
            int groundEnd = filled.get(i + 1);
            groundStarts.add(groundStart);
            groundEnds.add(groundEnd);
            groundSamples.add(groundStart);
        }
        if (!groundEnds.isEmpty()) {
            groundSamples.add(groundEnds.getLast());
        }

        return new SampleData(groundSamples, cumulativeDistances, groundStarts, groundEnds);
    }

    /**
     * 已加载站插值填补未加载缺口：两端外延最近已加载值，中间线性插值。
     * 全程未加载时才回退默认海平面，避免把 64 挖成一条假山谷。
     */
    static List<Integer> fillLoadedGaps(List<OptionalInt> samples) {
        if (samples == null || samples.isEmpty()) {
            return List.of();
        }
        int n = samples.size();
        Integer[] values = new Integer[n];
        List<Integer> loaded = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            OptionalInt sample = samples.get(i);
            if (sample != null && sample.isPresent()) {
                values[i] = sample.getAsInt();
                loaded.add(i);
            }
        }
        if (loaded.isEmpty()) {
            List<Integer> fallback = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                fallback.add(TerrainSampler.DEFAULT_SEA_LEVEL);
            }
            return fallback;
        }
        int first = loaded.getFirst();
        for (int i = 0; i < first; i++) {
            values[i] = values[first];
        }
        int last = loaded.getLast();
        for (int i = last + 1; i < n; i++) {
            values[i] = values[last];
        }
        for (int k = 0; k < loaded.size() - 1; k++) {
            int a = loaded.get(k);
            int b = loaded.get(k + 1);
            int ya = values[a];
            int yb = values[b];
            int span = b - a;
            for (int i = a + 1; i < b; i++) {
                double t = (double) (i - a) / span;
                values[i] = (int) Math.round(ya + t * (yb - ya));
            }
        }
        return List.of(values);
    }

    public static double sampledPathLength(List<PathSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return 0.0;
        }
        return segments.stream().mapToDouble(segment -> segment.distance).sum();
    }
}
