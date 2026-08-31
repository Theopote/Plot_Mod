package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.List;

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

        for (PathSegment segment : segments) {
            Vec2d tangent = segment.end.subtract(segment.start);
            int groundStart = terrain.sampleCrossSectionGroundY(segment.start, tangent, halfWidth);
            int groundEnd = terrain.sampleCrossSectionGroundY(segment.end, tangent, halfWidth);
            groundStarts.add(groundStart);
            groundEnds.add(groundEnd);
            groundSamples.add(groundStart);
            cumulativeDistances.add(accumulatedDistance);
            accumulatedDistance += segment.distance;
        }

        if (!groundEnds.isEmpty()) {
            groundSamples.add(groundEnds.getLast());
            cumulativeDistances.add(accumulatedDistance);
        }

        return new SampleData(groundSamples, cumulativeDistances, groundStarts, groundEnds);
    }

    public static double sampledPathLength(List<PathSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return 0.0;
        }
        return segments.stream().mapToDouble(segment -> segment.distance).sum();
    }
}
