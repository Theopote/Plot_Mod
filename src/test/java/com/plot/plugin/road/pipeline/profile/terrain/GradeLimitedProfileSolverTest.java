package com.plot.plugin.road.pipeline.profile.terrain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradeLimitedProfileSolverTest {

    @Test
    void bidirectionalSolverSpreadsStepTransitionWithinSlopeLimit() {
        List<Double> trend = List.of(
            60.0, 61.0, 62.0, 63.0, 64.0, 65.0, 66.0, 67.0, 68.0, 69.0, 70.0);
        List<Double> distances = constantDistances(trend.size() - 1, 10.0);
        List<Float> slopes = constantSlopes(distances.size(), 8.0f);

        double[] stations = GradeLimitedProfileSolver.solveStationElevations(
            trend, distances, slopes, null, null, TerrainFollowPreset.STANDARD);

        double maxStep = maxAdjacentDelta(stations);
        assertTrue(maxStep <= 0.8 + 1e-6,
            () -> "each 10 m segment at 8% should rise at most ~0.8 block, got " + maxStep);
        assertTrue(stations[stations.length - 1] >= stations[0]);
    }

    @Test
    void manualEndpointsArePinned() {
        List<Double> trend = List.of(60.0, 68.0, 75.0);
        List<Double> distances = List.of(20.0, 20.0);
        List<Float> slopes = List.of(8.0f, 8.0f);

        List<Integer> ends = GradeLimitedProfileSolver.solveSegmentEnds(
            trend, distances, slopes, 58, 80, TerrainFollowPreset.STANDARD);

        assertEquals(80, ends.getLast());
        assertTrue(ends.getFirst() > 58, "first segment end should climb from pinned start toward manual end");
    }

    @Test
    void stepTrendAvoidsLongFlatRunBeforeJump() {
        List<Double> stations = List.of(0.0, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0);
        List<Double> raw = List.of(
            60.0, 60.0, 60.0, 60.0, 60.0,
            75.0, 75.0, 75.0, 75.0, 75.0, 75.0);
        TerrainTrendResult trend = TerrainTrendBuilder.build(
            new TerrainProfileSampleChain(stations, raw),
            TerrainFollowPreset.STANDARD);
        List<Double> distances = constantDistances(stations.size() - 1, 10.0);
        List<Float> slopes = constantSlopes(distances.size(), 8.0f);

        List<Integer> profile = toProfile(
            trend.trendElevations().getFirst(),
            GradeLimitedProfileSolver.solveSegmentEnds(
                trend.trendElevations(), distances, slopes, null, null, TerrainFollowPreset.STANDARD));

        assertEquals(0, countLongFlatRuns(profile, 3),
            "grade-limited profile should spread step transitions without long flat runs");
        assertTrue(maxAdjacentDelta(profile) < 15.0,
            () -> "solver should spread the 15 m raw step, got jump " + maxAdjacentDelta(profile));
    }

    @Test
    void relaxationMovesResultCloserToTrendThanForwardOnlyPass() {
        List<Double> trend = List.of(60.0, 62.0, 68.0, 74.0, 75.0);
        List<Double> distances = constantDistances(4, 10.0);
        List<Float> slopes = constantSlopes(4, 10.0f);

        List<Integer> solved = GradeLimitedProfileSolver.solveSegmentEnds(
            trend, distances, slopes, null, null, TerrainFollowPreset.STANDARD);
        List<Integer> profile = toProfile(trend.getFirst(), solved);

        double solvedError = meanAbsoluteError(profile, trend);
        double forwardOnlyError = meanAbsoluteError(forwardOnlyProfile(trend, distances, slopes), trend);
        assertTrue(solvedError <= forwardOnlyError + 1.0,
            () -> "relaxed profile should track trend at least as well as forward-only");
    }

    private static List<Integer> forwardOnlyProfile(
            List<Double> trend,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents) {
        double[] elevations = new double[trend.size()];
        elevations[0] = trend.getFirst();
        for (int i = 0; i < segmentDistances.size(); i++) {
            double maxRise = segmentDistances.get(i) * maxSlopePercents.get(i) / 100.0;
            elevations[i + 1] = GradeLimitedProfileSolver.clampToward(
                elevations[i], trend.get(i + 1), maxRise);
        }
        List<Integer> profile = new ArrayList<>();
        for (double elevation : elevations) {
            profile.add((int) Math.round(elevation));
        }
        return profile;
    }

    private static List<Integer> toProfile(double start, List<Integer> segmentEnds) {
        List<Integer> profile = new ArrayList<>();
        profile.add((int) Math.round(start));
        profile.addAll(segmentEnds);
        return profile;
    }

    private static List<Double> constantDistances(int count, double distance) {
        List<Double> distances = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            distances.add(distance);
        }
        return distances;
    }

    private static List<Float> constantSlopes(int count, float slope) {
        List<Float> slopes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            slopes.add(slope);
        }
        return slopes;
    }

    private static double maxAdjacentDelta(double[] elevations) {
        double max = 0.0;
        for (int i = 1; i < elevations.length; i++) {
            max = Math.max(max, Math.abs(elevations[i] - elevations[i - 1]));
        }
        return max;
    }

    private static double maxAdjacentDelta(List<Integer> elevations) {
        double max = 0.0;
        for (int i = 1; i < elevations.size(); i++) {
            max = Math.max(max, Math.abs(elevations.get(i) - elevations.get(i - 1)));
        }
        return max;
    }

    private static int countLongFlatRuns(List<Integer> elevations, int minRunLength) {
        int longest = 0;
        int current = 1;
        for (int i = 1; i < elevations.size(); i++) {
            if (elevations.get(i).equals(elevations.get(i - 1))) {
                current++;
            } else {
                longest = Math.max(longest, current);
                current = 1;
            }
        }
        longest = Math.max(longest, current);
        return longest >= minRunLength ? longest : 0;
    }

    private static double meanAbsoluteError(List<Integer> actual, List<Double> desired) {
        double sum = 0.0;
        int count = Math.min(actual.size(), desired.size());
        for (int i = 0; i < count; i++) {
            sum += Math.abs(actual.get(i) - desired.get(i));
        }
        return count > 0 ? sum / count : Double.POSITIVE_INFINITY;
    }
}
