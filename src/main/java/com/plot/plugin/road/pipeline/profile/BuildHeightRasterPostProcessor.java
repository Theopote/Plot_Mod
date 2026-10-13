package com.plot.plugin.road.pipeline.profile;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Applies integer build-height smoothing after {@link RoadHeightRasterizer} and rebuilds
 * derived station summaries so preview and generation share one post-process path.
 */
public final class BuildHeightRasterPostProcessor {

    private static final double EPSILON = 1e-9;

    private BuildHeightRasterPostProcessor() {
    }

    public static RoadHeightRasterizer.RasterizationResult smooth(
            RoadHeightRasterizer.RasterizationResult raster,
            List<Double> segmentDistances,
            ProfileCompileOptions options) {
        return smooth(raster, segmentDistances, options, null);
    }

    public static RoadHeightRasterizer.RasterizationResult smooth(
            RoadHeightRasterizer.RasterizationResult raster,
            List<Double> segmentDistances,
            ProfileCompileOptions options,
            BitSet lockedSampleIndices) {
        if (raster == null || raster.samples() == null || raster.samples().isEmpty()) {
            return raster;
        }
        ProfileCompileOptions effective = options != null ? options : ProfileCompileOptions.defaults();
        BuildHeightSequenceSmoother.SmoothingResult smoothed =
            BuildHeightSequenceSmoother.smooth(raster.samples(), effective, lockedSampleIndices);
        return rebuild(raster, smoothed.samples(), segmentDistances);
    }

    public static List<BuildHeightSample> smoothSamples(
            List<BuildHeightSample> samples,
            ProfileCompileOptions options,
            BitSet lockedSampleIndices) {
        ProfileCompileOptions effective = options != null ? options : ProfileCompileOptions.defaults();
        return BuildHeightSequenceSmoother.smooth(samples, effective, lockedSampleIndices).samples();
    }

    private static RoadHeightRasterizer.RasterizationResult rebuild(
            RoadHeightRasterizer.RasterizationResult original,
            List<BuildHeightSample> samples,
            List<Double> segmentDistances) {
        if (samples.isEmpty()) {
            return original;
        }
        int startHeight = samples.getFirst().buildY();
        List<Double> cumulative = cumulativeDistances(segmentDistances);
        List<Integer> segmentBuildEnds = segmentBuildEndsFromSamples(
            samples, cumulative, segmentDistances != null ? segmentDistances.size() : 0);
        List<Integer> buildHeights = buildStationHeights(startHeight, segmentBuildEnds);
        List<Double> designAtStations = designAtStations(samples, cumulative);
        BuildHeightProfile buildProfile = BuildHeightProfile.fromSamples(samples);
        return new RoadHeightRasterizer.RasterizationResult(
            startHeight,
            buildHeights,
            segmentBuildEnds,
            samples,
            buildProfile,
            RoadHeightRasterizer.maxDesignBuildDeviation(designAtStations, buildHeights),
            RoadHeightRasterizer.cumulativeGradeError(designAtStations, buildHeights),
            RoadHeightRasterizer.longestFlatRun(samples),
            RoadHeightRasterizer.countSteps(samples));
    }

    private static List<Double> cumulativeDistances(List<Double> segmentDistances) {
        List<Double> cumulative = new ArrayList<>();
        cumulative.add(0.0);
        if (segmentDistances == null) {
            return cumulative;
        }
        double total = 0.0;
        for (double distance : segmentDistances) {
            total += distance;
            cumulative.add(total);
        }
        return cumulative;
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

    private static List<Integer> buildStationHeights(int startHeight, List<Integer> segmentBuildEnds) {
        List<Integer> buildHeights = new ArrayList<>(segmentBuildEnds.size() + 1);
        buildHeights.add(startHeight);
        buildHeights.addAll(segmentBuildEnds);
        return buildHeights;
    }

    private static List<Double> designAtStations(
            List<BuildHeightSample> samples,
            List<Double> cumulativeDistances) {
        List<Double> design = new ArrayList<>(cumulativeDistances.size());
        for (double station : cumulativeDistances) {
            BuildHeightSample match = samples.getFirst();
            for (BuildHeightSample sample : samples) {
                if (sample.station() <= station + EPSILON) {
                    match = sample;
                } else {
                    break;
                }
            }
            design.add(match.designElevation());
        }
        return design;
    }
}
