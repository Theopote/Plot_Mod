package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.RoadSlopeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 将连续设计纵断面离散为 Minecraft 方块级建造高程。
 * 在全局 chainage（约 1 block / station）上采样，坡度预算通过
 * {@link RoadSlopeUtils.ElevationAccumulator} 累积，避免 PathSegment 切分影响台阶分布。
 */
public final class RoadHeightRasterizer {

    private static final double EPSILON = 1e-9;

    private RoadHeightRasterizer() {
    }

    public record RasterizationResult(
            int startHeight,
            List<Integer> buildHeights,
            List<Integer> segmentBuildEnds,
            List<BuildHeightSample> samples,
            BuildHeightProfile buildProfile,
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
            return emptyResult();
        }
        if (designElevations == null || designElevations.size() != segmentDistances.size() + 1) {
            throw new IllegalArgumentException("design elevations must have one more sample than segments");
        }
        if (maxSlopePercents == null || maxSlopePercents.size() != segmentDistances.size()) {
            throw new IllegalArgumentException("max slope list must match segment count");
        }

        int startHeight = roundedStartHeight(designElevations, manualStartHeight);

        List<Double> cumulativeDistances = cumulativeDistances(segmentDistances);
        double totalLength = cumulativeDistances.getLast();
        List<BuildHeightSample> samples = rasterizeBlockSamples(
            designElevations,
            segmentDistances,
            cumulativeDistances,
            maxSlopePercents,
            totalLength,
            startHeight);

        List<Integer> segmentBuildEnds = segmentBuildEndsFromSamples(
            samples, cumulativeDistances, segmentDistances.size());
        List<Integer> buildHeights = buildStationHeights(startHeight, segmentBuildEnds);
        List<Double> designAtSegmentStations = designAtStations(designElevations, cumulativeDistances);
        BuildHeightProfile buildProfile = BuildHeightProfile.fromSamples(samples);

        return new RasterizationResult(
            startHeight,
            buildHeights,
            segmentBuildEnds,
            samples,
            buildProfile,
            maxDesignBuildDeviation(designAtSegmentStations, buildHeights),
            cumulativeGradeError(designAtSegmentStations, buildHeights),
            longestFlatRun(samples),
            countSteps(samples));
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

    private static RasterizationResult emptyResult() {
        return new RasterizationResult(
            0,
            List.of(),
            List.of(),
            List.of(),
            BuildHeightProfile.inactive(),
            0.0,
            0.0,
            0,
            0);
    }

    private static List<BuildHeightSample> rasterizeBlockSamples(
            List<Double> designElevations,
            List<Double> segmentDistances,
            List<Double> cumulativeDistances,
            List<Float> maxSlopePercents,
            double totalLength,
            int startHeight) {
        List<BuildHeightSample> samples = new ArrayList<>();
        int currentBuild = startHeight;
        RoadSlopeUtils.ElevationAccumulator accumulator = new RoadSlopeUtils.ElevationAccumulator();

        double designAtStart = interpolateDesignElevation(0.0, designElevations, segmentDistances, cumulativeDistances);
        samples.add(new BuildHeightSample(0.0, designAtStart, startHeight));

        int wholeBlocks = (int) Math.floor(totalLength + EPSILON);
        for (int block = 1; block <= wholeBlocks; block++) {
            double station = block;
            double designY = interpolateDesignElevation(
                station, designElevations, segmentDistances, cumulativeDistances);
            float maxSlope = maxSlopeAt(station, segmentDistances, cumulativeDistances, maxSlopePercents);
            currentBuild = advanceTowardContinuousElevation(
                currentBuild,
                designY,
                1.0,
                maxSlope,
                accumulator);
            samples.add(new BuildHeightSample(station, designY, currentBuild));
        }

        if (totalLength - wholeBlocks > EPSILON) {
            double designY = interpolateDesignElevation(
                totalLength, designElevations, segmentDistances, cumulativeDistances);
            float maxSlope = maxSlopeAt(totalLength, segmentDistances, cumulativeDistances, maxSlopePercents);
            double tailDistance = totalLength - wholeBlocks;
            currentBuild = advanceTowardContinuousElevation(
                currentBuild,
                designY,
                tailDistance,
                maxSlope,
                accumulator);
            samples.add(new BuildHeightSample(totalLength, designY, currentBuild));
        }

        return List.copyOf(samples);
    }

    private static List<Integer> segmentBuildEndsFromSamples(
            List<BuildHeightSample> samples,
            List<Double> cumulativeDistances,
            int segmentCount) {
        List<Integer> segmentEnds = new ArrayList<>(segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            double station = cumulativeDistances.get(i + 1);
            segmentEnds.add(buildYAtStation(samples, station));
        }
        return segmentEnds;
    }

    private static int advanceTowardContinuousElevation(
            int currentBuild,
            double targetElevation,
            double distance,
            float maxSlopePercent,
            RoadSlopeUtils.ElevationAccumulator accumulator) {
        double maxRise = distance * maxSlopePercent / 100.0;
        double delta = targetElevation - currentBuild;
        if (Math.abs(delta) <= maxRise + EPSILON) {
            return accumulator.advance(currentBuild, delta);
        }
        double continuousStep = delta > 0.0 ? maxRise : -maxRise;
        return accumulator.advance(currentBuild, continuousStep);
    }

    private static int buildYAtStation(List<BuildHeightSample> samples, double station) {
        BuildHeightSample match = samples.getFirst();
        for (BuildHeightSample sample : samples) {
            if (sample.station() <= station + EPSILON) {
                match = sample;
            } else {
                break;
            }
        }
        return match.buildY();
    }

    private static List<Double> cumulativeDistances(List<Double> segmentDistances) {
        List<Double> cumulative = new ArrayList<>(segmentDistances.size() + 1);
        cumulative.add(0.0);
        double total = 0.0;
        for (double distance : segmentDistances) {
            total += distance;
            cumulative.add(total);
        }
        return cumulative;
    }

    private static List<Double> designAtStations(
            List<Double> designElevations,
            List<Double> cumulativeDistances) {
        List<Double> designAtStations = new ArrayList<>(cumulativeDistances.size());
        for (double station : cumulativeDistances) {
            designAtStations.add(designElevations.get(indexAtStation(station, cumulativeDistances)));
        }
        return designAtStations;
    }

    static double interpolateDesignElevation(
            double station,
            List<Double> designElevations,
            List<Double> segmentDistances,
            List<Double> cumulativeDistances) {
        if (station <= EPSILON) {
            return designElevations.getFirst();
        }
        if (station >= cumulativeDistances.getLast() - EPSILON) {
            return designElevations.getLast();
        }
        int segmentIndex = segmentIndexAt(station, cumulativeDistances);
        double segmentStart = cumulativeDistances.get(segmentIndex);
        double segmentLength = segmentDistances.get(segmentIndex);
        double localT = segmentLength > EPSILON
            ? (station - segmentStart) / segmentLength
            : 0.0;
        double start = designElevations.get(segmentIndex);
        double end = designElevations.get(segmentIndex + 1);
        return start * (1.0 - localT) + end * localT;
    }

    static float maxSlopeAt(
            double station,
            List<Double> segmentDistances,
            List<Double> cumulativeDistances,
            List<Float> maxSlopePercents) {
        return maxSlopePercents.get(segmentIndexAt(station, cumulativeDistances));
    }

    private static int segmentIndexAt(double station, List<Double> cumulativeDistances) {
        int index = indexAtStation(station, cumulativeDistances);
        if (index >= cumulativeDistances.size() - 1) {
            return cumulativeDistances.size() - 2;
        }
        return index;
    }

    private static int indexAtStation(double station, List<Double> cumulativeDistances) {
        for (int i = cumulativeDistances.size() - 1; i >= 0; i--) {
            if (station + EPSILON >= cumulativeDistances.get(i)) {
                return i;
            }
        }
        return 0;
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

    static int longestFlatRun(List<BuildHeightSample> samples) {
        if (samples.size() < 2) {
            return samples.size();
        }
        int longest = 1;
        int current = 1;
        for (int i = 1; i < samples.size(); i++) {
            if (samples.get(i).buildY() == samples.get(i - 1).buildY()) {
                current++;
            } else {
                longest = Math.max(longest, current);
                current = 1;
            }
        }
        return Math.max(longest, current);
    }

    static int countSteps(List<BuildHeightSample> samples) {
        int steps = 0;
        for (int i = 1; i < samples.size(); i++) {
            if (samples.get(i).buildY() != samples.get(i - 1).buildY()) {
                steps++;
            }
        }
        return steps;
    }
}
