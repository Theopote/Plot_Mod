package com.plot.plugin.road.pipeline.profile.environment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.core.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;

/**
 * Samples solid terrain and exposed water along a path (parallel to {@link com.plot.plugin.road.pipeline.profile.ProfileGroundSampler}).
 */
public final class ProfileEnvironmentSampler {

    private ProfileEnvironmentSampler() {
    }

    public static EnvironmentProfile collect(
            List<PathSegment> segments,
            TerrainSampler terrain,
            double halfWidth) {
        List<EnvironmentSample> samples = new ArrayList<>();
        List<Double> cumulativeDistances = new ArrayList<>();
        double accumulatedDistance = 0.0;

        for (PathSegment segment : segments) {
            Vec2d tangent = segment.end.subtract(segment.start);
            EnvironmentColumn start = sampleColumn(terrain, segment.start, tangent, halfWidth);
            EnvironmentColumn end = sampleColumn(terrain, segment.end, tangent, halfWidth);
            samples.add(toSample(accumulatedDistance, start));
            cumulativeDistances.add(accumulatedDistance);
            accumulatedDistance += segment.distance;
        }

        if (!segments.isEmpty()) {
            PathSegment last = segments.getLast();
            Vec2d tangent = last.end.subtract(last.start);
            EnvironmentColumn endColumn = sampleColumn(terrain, last.end, tangent, halfWidth);
            samples.add(toSample(accumulatedDistance, endColumn));
            cumulativeDistances.add(accumulatedDistance);
        }

        return new EnvironmentProfile(List.copyOf(samples), List.copyOf(cumulativeDistances));
    }

    private static EnvironmentSample toSample(double station, EnvironmentColumn column) {
        Integer waterY = column.waterSurfaceY;
        int depth = 0;
        SurfaceContext context = SurfaceContext.LAND;
        if (waterY != null && waterY > column.terrainY) {
            depth = waterY - column.terrainY;
            context = depth <= 2 ? SurfaceContext.SHALLOW_WATER : SurfaceContext.DEEP_WATER;
        }
        return new EnvironmentSample(station, column.terrainY, waterY, depth, context);
    }

    private static EnvironmentColumn sampleColumn(
            TerrainSampler terrain,
            Vec2d center,
            Vec2d tangent,
            double halfWidth) {
        int terrainY = terrain.sampleCrossSectionGroundY(center, tangent, halfWidth);
        Integer waterY = sampleCrossSectionWaterY(terrain, center, tangent, halfWidth);
        return new EnvironmentColumn(terrainY, waterY);
    }

    static Integer sampleCrossSectionWaterY(
            TerrainSampler terrain,
            Vec2d center,
            Vec2d tangent,
            double halfWidth) {
        if (center == null) {
            return null;
        }
        if (halfWidth <= 0) {
            return optionalToInteger(terrain.findExposedWaterSurface(center));
        }
        Vec2d normal = leftNormal(tangent);
        LinkedHashSet<Integer> waterHeights = new LinkedHashSet<>();
        for (int offset : crossSectionSampleOffsets(halfWidth)) {
            Integer water = optionalToInteger(
                terrain.findExposedWaterSurface(center.add(normal.multiply(offset))));
            if (water != null) {
                waterHeights.add(water);
            }
        }
        if (waterHeights.isEmpty()) {
            return null;
        }
        int max = Integer.MIN_VALUE;
        for (int height : waterHeights) {
            max = Math.max(max, height);
        }
        return max;
    }

    private static Integer optionalToInteger(OptionalInt value) {
        return value.isPresent() ? value.getAsInt() : null;
    }

    private static Vec2d leftNormal(Vec2d direction) {
        if (direction == null || direction.lengthSquared() < 1e-12) {
            return new Vec2d(0, 1);
        }
        Vec2d unit = direction.normalize();
        return new Vec2d(-unit.y, unit.x);
    }

    private static List<Integer> crossSectionSampleOffsets(double halfWidth) {
        LinkedHashSet<Integer> offsets = new LinkedHashSet<>();
        offsets.add(0);
        offsets.add((int) Math.round(-halfWidth));
        offsets.add((int) Math.round(halfWidth));
        return List.copyOf(offsets);
    }

    private record EnvironmentColumn(int terrainY, Integer waterSurfaceY) {
    }
}
