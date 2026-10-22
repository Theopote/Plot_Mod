package com.plot.plugin.road.pipeline.profile.terrain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A/B: disabled oscillation merge must match baseline; enabled may only accept improvements. */
class OscillationReductionAbTest {

    @Test
    void enabledEitherImprovesOrMatchesDisabledBaseline() {
        List<Double> trend = List.of(
            64.0, 67.0, 65.0, 68.0, 66.0, 69.0, 64.0);
        List<Double> distances = constantDistances(trend.size() - 1, 7.0);
        List<Float> slopes = constantSlopes(distances.size(), 12.0f);

        double[] disabled = GradeLimitedProfileSolver.solveStationElevations(
            trend, null, distances, slopes, null, null,
            TerrainFollowPreset.STANDARD, null, false);
        double[] enabled = GradeLimitedProfileSolver.solveStationElevations(
            trend, null, distances, slopes, null, null,
            TerrainFollowPreset.STANDARD, null, true);

        OscillationReductionSummary summary = GradeLimitedProfileSolver.lastOscillationReduction();
        if (!summary.accepted()) {
            assertArrayEquals(disabled, enabled, 1e-6,
                "rejected merge must leave baseline unchanged");
            return;
        }
        assertTrue(summary.gradeReversalsAfter() <= summary.gradeReversalsBefore(),
            "accepted merge must not increase grade reversals");
        assertTrue(maxBuildAdjacentDelta(enabled, distances, slopes)
                <= maxBuildAdjacentDelta(disabled, distances, slopes) + 1e-6,
            "accepted merge must not worsen build adjacent delta");
    }

    @Test
    void enabledDoesNotIncreaseBuildAdjacentDeltaWhenAccepted() {
        List<Double> trend = List.of(
            60.0, 63.0, 60.0, 63.0, 60.0, 63.0, 60.0, 63.0, 60.0, 63.0, 60.0);
        List<Double> distances = constantDistances(trend.size() - 1, 10.0);
        List<Float> slopes = constantSlopes(distances.size(), 8.0f);

        double[] disabled = GradeLimitedProfileSolver.solveStationElevations(
            trend, null, distances, slopes, null, null,
            TerrainFollowPreset.STANDARD, null, false);
        double[] enabled = GradeLimitedProfileSolver.solveStationElevations(
            trend, null, distances, slopes, null, null,
            TerrainFollowPreset.STANDARD, null, true);

        OscillationReductionSummary summary = GradeLimitedProfileSolver.lastOscillationReduction();
        double disabledBuildDelta = maxBuildAdjacentDelta(disabled, distances, slopes);
        double enabledBuildDelta = maxBuildAdjacentDelta(enabled, distances, slopes);
        assertTrue(enabledBuildDelta <= disabledBuildDelta + 1e-6,
            () -> "accepted merge must not worsen build adjacent delta: "
                + disabledBuildDelta + " vs " + enabledBuildDelta);
        if (summary.accepted()) {
            int disabledReversals = ProfileOscillationReducer.countGradeSignReversals(
                disabled, distances);
            int enabledReversals = ProfileOscillationReducer.countGradeSignReversals(
                enabled, distances);
            assertTrue(enabledReversals <= disabledReversals,
                () -> "accepted merge should not increase grade reversals");
        }
    }

    @Test
    void longProfileCompletesWithinReasonableTime() {
        int stationCount = 501;
        List<Double> trend = new java.util.ArrayList<>(stationCount);
        for (int i = 0; i < stationCount; i++) {
            trend.add(64.0 + (i % 4 == 1 || i % 4 == 3 ? 2.0 : 0.0));
        }
        List<Double> distances = constantDistances(stationCount - 1, 10.0);
        List<Float> slopes = constantSlopes(distances.size(), 8.0f);

        long startNanos = System.nanoTime();
        GradeLimitedProfileSolver.solveStationElevations(
            trend, null, distances, slopes, null, null,
            TerrainFollowPreset.STANDARD, null, true);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        assertTrue(elapsedMs < 5_000,
            () -> "oscillation merge on 500-station profile should finish quickly, took " + elapsedMs + "ms");
    }

    private static double maxBuildAdjacentDelta(
            double[] design,
            List<Double> distances,
            List<Float> slopes) {
        com.plot.plugin.road.pipeline.profile.RoadHeightRasterizer.RasterizationResult raster =
            com.plot.plugin.road.pipeline.profile.BuildHeightRasterPostProcessor.smooth(
                com.plot.plugin.road.pipeline.profile.RoadHeightRasterizer.rasterize(
                    toList(design), distances, slopes, null),
                distances,
                com.plot.plugin.road.pipeline.profile.ProfileCompileOptions.defaults());
        double max = 0.0;
        List<Integer> build = raster.buildHeights();
        for (int i = 1; i < build.size(); i++) {
            max = Math.max(max, Math.abs(build.get(i) - build.get(i - 1)));
        }
        return max;
    }

    private static List<Double> toList(double[] values) {
        List<Double> list = new java.util.ArrayList<>(values.length);
        for (double value : values) {
            list.add(value);
        }
        return list;
    }

    private static List<Double> constantDistances(int count, double distance) {
        List<Double> distances = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            distances.add(distance);
        }
        return distances;
    }

    private static List<Float> constantSlopes(int count, float slope) {
        List<Float> slopes = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            slopes.add(slope);
        }
        return slopes;
    }
}
