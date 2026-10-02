package com.plot.plugin.road.pipeline.profile.environment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.pipeline.geometry.PathSegment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Uniform chainage grid along a path, decoupled from {@link PathSegment} endpoints. */
public final class ProfileStationGrid {

    private static final double EPSILON = 1e-9;

    private ProfileStationGrid() {
    }

    public record StationPoint(double station, Vec2d point, Vec2d tangent) {
    }

    public static List<StationPoint> alongPath(List<PathSegment> segments, double spacingCanvas) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        double effectiveSpacing = spacingCanvas > EPSILON ? spacingCanvas : 1.0;
        Set<Long> seenStations = new LinkedHashSet<>();
        List<StationPoint> points = new ArrayList<>();
        double accumulated = 0.0;
        for (PathSegment segment : segments) {
            double segmentLength = Math.max(0.0, segment.distance);
            Vec2d tangent = segment.end.subtract(segment.start);
            if (segmentLength <= EPSILON) {
                addPoint(points, seenStations, accumulated, segment.start, tangent);
                continue;
            }
            int interiorCount = Math.max(0, (int) Math.floor(segmentLength / effectiveSpacing));
            for (int step = 0; step <= interiorCount; step++) {
                double offset = Math.min(segmentLength, step * effectiveSpacing);
                double station = accumulated + offset;
                double blend = offset / segmentLength;
                Vec2d point = segment.start.add(tangent.multiply(blend));
                addPoint(points, seenStations, station, point, tangent);
            }
            if (interiorCount * effectiveSpacing + EPSILON < segmentLength) {
                addPoint(points, seenStations, accumulated + segmentLength, segment.end, tangent);
            }
            accumulated += segmentLength;
        }
        return List.copyOf(points);
    }

    public static double resolveSpacingCanvas(
            double canvasUnitsPerBlock,
            double pathSampleDistanceMeters,
            double environmentSampleSpacingMeters) {
        double scale = canvasUnitsPerBlock > EPSILON ? canvasUnitsPerBlock : 1.0;
        double pathSpacingCanvas = pathSampleDistanceMeters * scale;
        double environmentSpacingCanvas = environmentSampleSpacingMeters * scale;
        double chosenMeters = Math.min(pathSampleDistanceMeters, environmentSampleSpacingMeters);
        return Math.max(EPSILON, chosenMeters * scale);
    }

    private static void addPoint(
            List<StationPoint> points,
            Set<Long> seenStations,
            double station,
            Vec2d point,
            Vec2d tangent) {
        long key = Math.round(station * 1_000_000.0);
        if (!seenStations.add(key)) {
            return;
        }
        points.add(new StationPoint(station, point, tangent));
    }
}
