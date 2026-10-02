package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.RoadSlopeUtils;
import com.plot.plugin.road.pipeline.profile.terrain.GradeLimitedProfileSolver;

import java.util.ArrayList;
import java.util.List;

/**
 * 将连续设计纵断面离散为 Minecraft 方块级建造高程。
 * 坡度预算通过 {@link RoadSlopeUtils.ElevationAccumulator} 在长距离上累积，而非逐段 ceil。
 */
public final class RoadHeightRasterizer {

    private RoadHeightRasterizer() {
    }

    public record RasterizationResult(
            int startHeight,
            List<Integer> buildHeights,
            List<Integer> segmentBuildEnds,
            double maxDesignBuildDeviation,
            double cumulativeGradeError,
            int longestFlatRun,
            int stepCount) {
    }

    /**
     * @param designElevations station-level design elevations, length = segments + 1
     */
    public static RasterizationResult rasterize(
            List<Double> designElevations,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            Integer manualStartHeight,
            Integer manualEndHeight) {
        if (segmentDistances == null || segmentDistances.isEmpty()) {
            return new RasterizationResult(0, List.of(), List.of(), 0.0, 0.0, 0, 0);
        }
        if (designElevations == null || designElevations.size() != segmentDistances.size() + 1) {
            throw new IllegalArgumentException("design elevations must have one more sample than segments");
        }
        if (maxSlopePercents == null || maxSlopePercents.size() != segmentDistances.size()) {
            throw new IllegalArgumentException("max slope list must match segment count");
        }

        int startHeight = roundedStartHeight(designElevations, manualStartHeight);
        boolean endpointsFeasible = GradeLimitedProfileSolver.areManualEndpointsFeasible(
            manualStartHeight,
            manualEndHeight,
            segmentDistances,
            maxSlopePercents,
            startHeight);
        Integer effectiveManualEndHeight = endpointsFeasible ? manualEndHeight : null;

        List<Integer> segmentBuildEnds = rasterizeSegmentEnds(
            designElevations,
            segmentDistances,
            maxSlopePercents,
            startHeight,
            effectiveManualEndHeight);
        List<Integer> buildHeights = buildStationHeights(startHeight, segmentBuildEnds);

        return new RasterizationResult(
            startHeight,
            buildHeights,
            segmentBuildEnds,
            maxDesignBuildDeviation(designElevations, buildHeights),
            cumulativeGradeError(designElevations, buildHeights),
            longestFlatRun(buildHeights),
            countSteps(buildHeights));
    }

    /**
     * Rasterize integer design stations (e.g. AUTO_SMOOTH / MANUAL_PROFILE) to build heights.
     */
    public static RasterizationResult rasterizeFromIntegerDesign(
            List<Integer> designStations,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            Integer manualStartHeight,
            Integer manualEndHeight) {
        List<Double> designElevations = new ArrayList<>(designStations.size());
        for (int value : designStations) {
            designElevations.add((double) value);
        }
        return rasterize(
            designElevations,
            segmentDistances,
            maxSlopePercents,
            manualStartHeight,
            manualEndHeight);
    }

    private static List<Integer> rasterizeSegmentEnds(
            List<Double> designElevations,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            int startHeight,
            Integer manualEndHeight) {
        List<Integer> segmentEnds = new ArrayList<>(segmentDistances.size());
        int previous = startHeight;
        RoadSlopeUtils.ElevationAccumulator accumulator = new RoadSlopeUtils.ElevationAccumulator();
        for (int i = 0; i < segmentDistances.size(); i++) {
            int ideal = (int) Math.round(designElevations.get(i + 1));
            int target = ideal;
            if (manualEndHeight != null && i == segmentDistances.size() - 1) {
                target = manualEndHeight;
            }
            int clamped = RoadSlopeUtils.clampTowardTarget(
                previous,
                target,
                segmentDistances.get(i),
                maxSlopePercents.get(i),
                accumulator);
            segmentEnds.add(clamped);
            previous = clamped;
        }
        return segmentEnds;
    }

    private static List<Integer> buildStationHeights(int startHeight, List<Integer> segmentBuildEnds) {
        List<Integer> buildHeights = new ArrayList<>(segmentBuildEnds.size() + 1);
        buildHeights.add(startHeight);
        buildHeights.addAll(segmentBuildEnds);
        return buildHeights;
    }

    private static int roundedStartHeight(List<Double> designElevations, Integer manualStartHeight) {
        if (manualStartHeight != null) {
            return manualStartHeight;
        }
        return (int) Math.round(designElevations.getFirst());
    }

    static double maxDesignBuildDeviation(List<Double> designElevations, List<Integer> buildHeights) {
        double max = 0.0;
        int count = Math.min(designElevations.size(), buildHeights.size());
        for (int i = 0; i < count; i++) {
            max = Math.max(max, Math.abs(designElevations.get(i) - buildHeights.get(i)));
        }
        return max;
    }

    static double cumulativeGradeError(List<Double> designElevations, List<Integer> buildHeights) {
        if (designElevations.isEmpty() || buildHeights.isEmpty()) {
            return 0.0;
        }
        double designDelta = designElevations.getLast() - designElevations.getFirst();
        double buildDelta = buildHeights.getLast() - buildHeights.getFirst();
        return Math.abs(buildDelta - designDelta);
    }

    static int longestFlatRun(List<Integer> buildHeights) {
        if (buildHeights.size() < 2) {
            return buildHeights.size();
        }
        int longest = 1;
        int current = 1;
        for (int i = 1; i < buildHeights.size(); i++) {
            if (buildHeights.get(i).equals(buildHeights.get(i - 1))) {
                current++;
            } else {
                longest = Math.max(longest, current);
                current = 1;
            }
        }
        return Math.max(longest, current);
    }

    static int countSteps(List<Integer> buildHeights) {
        int steps = 0;
        for (int i = 1; i < buildHeights.size(); i++) {
            if (!buildHeights.get(i).equals(buildHeights.get(i - 1))) {
                steps++;
            }
        }
        return steps;
    }
}
