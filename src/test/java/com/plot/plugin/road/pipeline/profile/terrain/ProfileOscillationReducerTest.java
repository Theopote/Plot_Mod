package com.plot.plugin.road.pipeline.profile.terrain;

import com.plot.plugin.road.pipeline.profile.environment.VerticalStationConstraints;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileOscillationReducerTest {

    @Test
    void mergesShortDistanceThreeHillZigZag() {
        double[] elevations = {64.0, 66.0, 64.5, 66.5, 65.0, 67.0, 64.0};
        List<Double> distances = constantDistances(elevations.length - 1, 7.0);
        List<Float> slopes = constantSlopes(distances.size(), 12.0f);
        int before = ProfileOscillationReducer.countGradeSignReversals(elevations, distances);

        ProfileOscillationReducer.reduceIfBeneficial(
            elevations,
            distances,
            slopes,
            TerrainFollowPreset.STANDARD,
            false,
            false,
            elevations[0],
            elevations[elevations.length - 1],
            null,
            null,
            null);

        int after = ProfileOscillationReducer.countGradeSignReversals(elevations, distances);
        assertTrue(after < before,
            () -> "short zig-zag should lose reversals: " + before + " -> " + after);
        assertTrue(after <= before / 2,
            () -> "reversal count should drop materially, got " + after + " from " + before);
    }

    @Test
    void doesNotMergeLargeValley() {
        double[] elevations = {80.0, 78.0, 74.0, 68.0, 65.0, 68.0, 74.0, 78.0, 80.0};
        double[] original = elevations.clone();
        List<Double> distances = constantDistances(elevations.length - 1, 10.0);
        List<Float> slopes = constantSlopes(distances.size(), 10.0f);
        int before = ProfileOscillationReducer.countGradeSignReversals(elevations, distances);

        ProfileOscillationReducer.reduceIfBeneficial(
            elevations,
            distances,
            slopes,
            TerrainFollowPreset.STANDARD,
            false,
            false,
            elevations[0],
            elevations[elevations.length - 1],
            null,
            null,
            null);

        int after = ProfileOscillationReducer.countGradeSignReversals(elevations, distances);
        assertArrayEquals(original, elevations, 1e-9);
        assertEquals(before, after, "large valley should not be merged");
    }

    @Test
    void preservesLockedEndpoints() {
        List<Double> trend = List.of(
            60.0, 63.0, 60.0, 63.0, 60.0, 63.0, 60.0, 63.0, 60.0, 63.0, 60.0);
        List<Double> distances = constantDistances(trend.size() - 1, 10.0);
        List<Float> slopes = constantSlopes(distances.size(), 8.0f);

        double[] solved = GradeLimitedProfileSolver.solveStationElevations(
            trend,
            null,
            distances,
            slopes,
            70,
            70,
            TerrainFollowPreset.STANDARD,
            null);

        assertEquals(70.0, solved[0], 1e-6);
        assertEquals(70.0, solved[solved.length - 1], 1e-6);
    }

    @Test
    void respectsWaterMinimumElevations() {
        double[] elevations = {64.0, 66.0, 64.5, 66.5, 65.0, 67.0, 64.0};
        List<Double> distances = constantDistances(elevations.length - 1, 7.0);
        List<Float> slopes = constantSlopes(distances.size(), 12.0f);
        double[] mins = new double[elevations.length];
        Arrays.fill(mins, 63.0);

        ProfileOscillationReducer.reduceIfBeneficial(
            elevations,
            distances,
            slopes,
            TerrainFollowPreset.STANDARD,
            false,
            false,
            elevations[0],
            elevations[elevations.length - 1],
            mins,
            null,
            null);

        for (int i = 0; i < elevations.length; i++) {
            double elevation = elevations[i];
            double minimum = mins[i];
            assertTrue(elevation >= minimum - 1e-6,
                "station " + i + " below water minimum: " + elevation);
        }
    }

    @Test
    void leavesFlatProfileUnchangedWhenNoBenefit() {
        double[] elevations = {64.0, 64.5, 65.0, 65.5, 66.0, 66.5, 67.0};
        double[] original = elevations.clone();
        List<Double> distances = constantDistances(elevations.length - 1, 10.0);
        List<Float> slopes = constantSlopes(distances.size(), 8.0f);
        List<Double> trend = new ArrayList<>();
        for (double elevation : elevations) {
            trend.add(elevation);
        }

        ProfileOscillationReducer.reduceIfBeneficial(
            elevations,
            distances,
            slopes,
            TerrainFollowPreset.STANDARD,
            false,
            false,
            elevations[0],
            elevations[elevations.length - 1],
            null,
            null,
            trend);

        assertArrayEquals(original, elevations, 1e-9);
        assertEquals(0, ProfileOscillationReducer.countGradeSignReversals(elevations, distances));
    }

    @Test
    void defaultSolverSkipsOscillationReducerUnlessExplicitlyForced() {
        List<Double> trend = List.of(
            64.0, 67.0, 65.0, 68.0, 66.0, 69.0, 64.0);
        List<Double> distances = constantDistances(trend.size() - 1, 7.0);
        List<Float> slopes = constantSlopes(distances.size(), 12.0f);

        double[] solved = GradeLimitedProfileSolver.solveStationElevations(
            trend, distances, slopes, TerrainFollowPreset.STANDARD);
        OscillationReductionSummary result = GradeLimitedProfileSolver.lastOscillationReduction();
        assertTrue(!result.attempted(),
            "oscillation reducer should be disabled by default on all presets");

        FitTerrainProfileInvariants.assertDesignProfileInvariants(
            toList(solved),
            trend.stream().map(v -> (int) Math.round(v)).toList(),
            distances,
            slopes,
            TerrainFollowPreset.STANDARD,
            null,
            null,
            true);
    }

    @Test
    void forcedCandidateMergeReducesOscillationOnThreeHills() {
        List<Double> trend = List.of(
            64.0, 67.0, 65.0, 68.0, 66.0, 69.0, 64.0);
        List<Double> distances = constantDistances(trend.size() - 1, 7.0);
        List<Float> slopes = constantSlopes(distances.size(), 12.0f);
        double[] elevations = trend.stream().mapToDouble(Double::doubleValue).toArray();
        int before = ProfileOscillationReducer.countGradeSignReversals(elevations, distances);

        ProfileOscillationReducer.reduceIfBeneficial(
            elevations, distances, slopes, TerrainFollowPreset.STANDARD,
            false, false, elevations[0], elevations[elevations.length - 1],
            null, null, trend);

        int after = ProfileOscillationReducer.countGradeSignReversals(elevations, distances);
        assertTrue(after < before,
            () -> "forced merge should reduce oscillation: " + before + " -> " + after);
    }

    @Test
    void projectOntoFeasibleDomainDoesNotApplyOscillationReducer() {
        List<Double> zigzag = List.of(
            64.0, 66.0, 64.5, 66.5, 65.0, 67.0, 64.0);
        List<Double> distances = constantDistances(zigzag.size() - 1, 7.0);
        List<Float> slopes = constantSlopes(distances.size(), 12.0f);
        VerticalStationConstraints.StationElevationBounds bounds =
            VerticalStationConstraints.StationElevationBounds.unbounded(zigzag.size());

        int before = countGradeSignReversals(zigzag, distances);
        List<Double> projected = GradeLimitedProfileSolver.projectOntoFeasibleDomain(
            zigzag,
            distances,
            slopes,
            TerrainFollowPreset.STANDARD,
            null,
            null,
            bounds,
            true);
        int after = countGradeSignReversals(projected, distances);
        assertTrue(after <= before,
            "feasible projection must not run oscillation merge; dampening may still help");
    }

    private static List<Double> constantDistances(int count, double distance) {
        List<Double> distances = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            distances.add(distance);
        }
        return distances;
    }

    private static List<Float> constantSlopes(int count, float slope) {
        List<Float> slopes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            slopes.add(slope);
        }
        return slopes;
    }

    private static List<Double> toList(double[] values) {
        List<Double> list = new ArrayList<>(values.length);
        for (double value : values) {
            list.add(value);
        }
        return list;
    }

    private static int countGradeSignReversals(List<Double> elevations, List<Double> distances) {
        return ProfileOscillationReducer.countGradeSignReversals(
            elevations.stream().mapToDouble(Double::doubleValue).toArray(), distances);
    }
}
