package com.plot.plugin.road.pipeline.geometry;

import com.plot.api.geometry.Vec2d;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Samples a centerline polyline into {@link PathSegment}s at a configurable spacing.
 */
public final class RoadGeometrySampler {
    private RoadGeometrySampler() {
    }

    public static List<PathSegment> sample(
            List<Vec2d> pathPoints,
            double pathSampleDistance,
            CanvasUnitsPerBlockEstimator canvasUnitsPerBlockEstimator) {
        double worldSampleDistance = Math.max(0.1, Math.min(10.0, pathSampleDistance));
        double canvasUnitsPerBlock = canvasUnitsPerBlockEstimator.estimate(pathPoints, null);
        double minSampleDistance = worldSampleDistance * Math.max(0.05, Math.min(canvasUnitsPerBlock, 500.0));

        List<PathSegment> segments = estimateSegmentCapacity(pathPoints, minSampleDistance);

        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Vec2d start = pathPoints.get(i);
            Vec2d end = pathPoints.get(i + 1);
            double distance = start.distance(end);

            if (distance < minSampleDistance) {
                segments.add(new PathSegment(start, end));
            } else {
                int samples = (int) Math.ceil(distance / minSampleDistance);
                Vec2d prev = start;
                for (int j = 1; j <= samples; j++) {
                    double t = (double) j / samples;
                    Vec2d current = start.lerp(end, t);
                    segments.add(new PathSegment(prev, current));
                    prev = current;
                }
            }
        }

        return segments;
    }

    private static @NotNull List<PathSegment> estimateSegmentCapacity(
            List<Vec2d> pathPoints,
            double minSampleDistance) {
        int estimatedSegments = 0;
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Vec2d start = pathPoints.get(i);
            Vec2d end = pathPoints.get(i + 1);
            double distance = start.distance(end);
            if (distance < minSampleDistance) {
                estimatedSegments += 1;
            } else {
                estimatedSegments += (int) Math.ceil(distance / minSampleDistance);
            }
        }

        return new ArrayList<>(estimatedSegments);
    }

    @FunctionalInterface
    public interface CanvasUnitsPerBlockEstimator {
        double estimate(List<Vec2d> pathPoints, List<PathSegment> segments);
    }
}
