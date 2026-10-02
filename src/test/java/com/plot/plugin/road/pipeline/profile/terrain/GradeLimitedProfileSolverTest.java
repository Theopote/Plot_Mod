package com.plot.plugin.road.pipeline.profile.terrain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void feasibleManualEndpointsAreReachedWithoutFinalSlopeViolation() {
        List<Double> trend = List.of(60.0, 59.0, 61.0);
        List<Double> distances = List.of(20.0, 20.0);
        List<Float> slopes = List.of(8.0f, 8.0f);

        GradeLimitedProfileSolver.SegmentEndSolveResult result =
            GradeLimitedProfileSolver.solveSegmentEndsWithStart(
                trend, distances, slopes, 58, 60, TerrainFollowPreset.STANDARD);

        assertTrue(result.manualEndpointsFeasible());
        assertEquals(60, result.segmentEnds().getLast());
        assertTrue(result.segmentEnds().getFirst() > 58);
    }

    @Test
    void infeasibleManualEndpointsAreNotForcedOntoLastSegment() {
        List<Double> trend = List.of(60.0, 68.0, 75.0);
        List<Double> distances = List.of(20.0, 20.0);
        List<Float> slopes = List.of(8.0f, 8.0f);

        GradeLimitedProfileSolver.SegmentEndSolveResult result =
            GradeLimitedProfileSolver.solveSegmentEndsWithStart(
                trend, distances, slopes, 58, 80, TerrainFollowPreset.STANDARD);

        assertFalse(result.manualEndpointsFeasible());
        assertTrue(result.segmentEnds().getLast() < 80,
            "infeasible manual end should climb only within slope budget");
        assertTrue(result.segmentEnds().getLast() <= 61,
            () -> "40 m at 8% allows at most ~3 blocks rise from 58, got " + result.segmentEnds().getLast());
    }

    @Test
    void fractionalSlopeBudgetDoesNotCeilToFullBlockStep() {
        List<Double> trend = List.of(60.0, 60.5, 61.0, 61.5, 62.0);
        List<Double> distances = constantDistances(4, 10.0);
        List<Float> slopes = constantSlopes(4, 5.0f);

        List<Integer> profile = toProfile(
            60,
            GradeLimitedProfileSolver.solveSegmentEnds(
                trend, distances, slopes, null, null, TerrainFollowPreset.STANDARD));

        assertEquals(60, profile.get(1),
            "5% over 10 m should not quantize to +1 block in the first segment");
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
    void gentlePresetSmoothsGradeChangesMoreThanTightOnOscillatingTrend() {
        List<Double> trend = List.of(
            60.0, 63.0, 60.0, 63.0, 60.0, 63.0, 60.0, 63.0, 60.0, 63.0, 60.0);
        List<Double> distances = constantDistances(trend.size() - 1, 10.0);
        List<Float> slopes = constantSlopes(distances.size(), 8.0f);

        double[] gentle = GradeLimitedProfileSolver.solveStationElevations(
            trend, distances, slopes, null, null, TerrainFollowPreset.GENTLE);
        double[] tight = GradeLimitedProfileSolver.solveStationElevations(
            trend, distances, slopes, null, null, TerrainFollowPreset.TIGHT);

        double gentleVariation = GradeLimitedProfileSolver.totalAbsoluteGradeChange(gentle, distances);
        double tightVariation = GradeLimitedProfileSolver.totalAbsoluteGradeChange(tight, distances);
        assertTrue(gentleVariation < tightVariation,
            () -> "gentle should reduce grade reversals more than tight: "
                + gentleVariation + " vs " + tightVariation);
        assertTrue(gentleVariation < tightVariation * 0.85,
            () -> "grade smoothing should materially reduce oscillation, got " + gentleVariation);
    }

    @Test
    void gradeChangeProjectionRespectsHardCap() {
        List<Double> trend = List.of(60.0, 68.0, 60.0, 68.0, 60.0);
        List<Double> distances = constantDistances(4, 10.0);
        List<Float> slopes = constantSlopes(4, 10.0f);

        double[] stations = GradeLimitedProfileSolver.solveStationElevations(
            trend, distances, slopes, null, null, TerrainFollowPreset.STANDARD);

        for (int i = 1; i < stations.length - 1; i++) {
            double leftGrade = GradeLimitedProfileSolver.gradeAtSegment(
                stations[i - 1], stations[i], distances.get(i - 1));
            double rightGrade = GradeLimitedProfileSolver.gradeAtSegment(
                stations[i], stations[i + 1], distances.get(i));
            int stationIndex = i;
            assertTrue(Math.abs(rightGrade - leftGrade)
                    <= TerrainFollowPreset.STANDARD.maxGradeChangePercent() + 1e-6,
                () -> "grade change at station " + stationIndex + " exceeded cap: "
                    + leftGrade + " -> " + rightGrade);
        }
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
                "relaxed profile should track trend at least as well as forward-only");
    }

    @Test
    void equalGradeElevationInterpolatesDistanceWeightedMidpoint() {
        assertEquals(62.0, GradeLimitedProfileSolver.equalGradeElevationAt(60.0, 64.0, 10.0, 10.0), 1e-9);
        assertEquals(62.0 + 2.0 / 3.0,
            GradeLimitedProfileSolver.equalGradeElevationAt(60.0, 64.0, 20.0, 10.0),
            1e-9);
    }

    @Test
    void linearGradeRampNetElevationChangeIsZeroForSymmetricCrest() {
        double end = GradeLimitedProfileSolver.elevationAlongLinearGradeRamp(
            65.0, 5.0, -5.0, 20.0, 20.0);
        assertEquals(65.0, end, 1e-6,
            "symmetric +5% to -5% ramp should return to start elevation at transition end");
    }

    @Test
    void resolveGradeTransitionLengthUsesPresetMinimum() {
        double gentle = GradeLimitedProfileSolver.resolveGradeTransitionLength(
            TerrainFollowPreset.GENTLE, 5.0, 5.0);
        double tight = GradeLimitedProfileSolver.resolveGradeTransitionLength(
            TerrainFollowPreset.TIGHT, 5.0, 5.0);
        assertTrue(gentle >= TerrainFollowPreset.GENTLE.minGradeTransitionMeters());
        assertTrue(tight >= TerrainFollowPreset.TIGHT.minGradeTransitionMeters());
        assertTrue(gentle > tight);
    }

    @Test
    void spreadGradeTransitionSoftensSharpCrestVertex() {
        List<Double> distances = constantDistances(10, 10.0);
        double[] elevations = {
            60.0, 60.5, 61.0, 61.5, 62.0,
            62.5, 62.0, 61.5, 61.0, 60.5, 60.0
        };
        double[] chainage = cumulativeChainage(distances);
        double[] before = elevations.clone();

        GradeLimitedProfileSolver.spreadGradeTransitionAtVertex(
            elevations, chainage, 5, 5.0, -5.0, 20.0);

        double beforeBreak = Math.abs(
            GradeLimitedProfileSolver.gradeAtSegment(before[4], before[5], 10.0)
                - GradeLimitedProfileSolver.gradeAtSegment(before[5], before[6], 10.0));
        double afterBreak = Math.abs(
            GradeLimitedProfileSolver.gradeAtSegment(elevations[4], elevations[5], 10.0)
                - GradeLimitedProfileSolver.gradeAtSegment(elevations[5], elevations[6], 10.0));
        assertTrue(afterBreak < beforeBreak,
            () -> "spread should reduce crest grade break from " + beforeBreak + " to " + afterBreak);
        assertTrue(maxAdjacentDelta(elevations) <= maxAdjacentDelta(before) + 1e-6);
    }

    @Test
    void gradeTransitionSoftensSharpCrestInDesignProfile() {
        List<Double> trend = List.of(
            60.0, 60.5, 61.0, 61.5, 62.0,
            62.5, 62.0, 61.5, 61.0, 60.5, 60.0);
        List<Double> distances = constantDistances(trend.size() - 1, 10.0);
        List<Float> slopes = constantSlopes(distances.size(), 10.0f);

        double[] solved = GradeLimitedProfileSolver.solveStationElevations(
            trend, distances, slopes, null, null, TerrainFollowPreset.STANDARD);

        int crestVertex = 5;
        double trendBreak = Math.abs(
            GradeLimitedProfileSolver.gradeAtSegment(
                trend.get(crestVertex - 1), trend.get(crestVertex), distances.get(crestVertex - 1))
                - GradeLimitedProfileSolver.gradeAtSegment(
                    trend.get(crestVertex), trend.get(crestVertex + 1), distances.get(crestVertex)));
        double solvedBreak = Math.abs(
            GradeLimitedProfileSolver.gradeAtSegment(
                solved[crestVertex - 1], solved[crestVertex], distances.get(crestVertex - 1))
                - GradeLimitedProfileSolver.gradeAtSegment(
                    solved[crestVertex], solved[crestVertex + 1], distances.get(crestVertex)));
        assertTrue(solvedBreak < trendBreak,
            () -> "solver should soften crest grade break from " + trendBreak + " to " + solvedBreak);
    }

    @Test
    void gentlePresetSpreadsGradeTransitionsMoreThanTight() {
        List<Double> trend = List.of(
            60.0, 61.0, 62.0, 63.0, 64.0,
            63.0, 62.0, 61.0, 60.0, 59.0, 58.0);
        List<Double> distances = constantDistances(trend.size() - 1, 10.0);
        List<Float> slopes = constantSlopes(distances.size(), 10.0f);

        double[] gentle = GradeLimitedProfileSolver.solveStationElevations(
            trend, distances, slopes, null, null, TerrainFollowPreset.GENTLE);
        double[] tight = GradeLimitedProfileSolver.solveStationElevations(
            trend, distances, slopes, null, null, TerrainFollowPreset.TIGHT);

        assertTrue(GradeLimitedProfileSolver.totalAbsoluteGradeChange(gentle, distances)
            <= GradeLimitedProfileSolver.totalAbsoluteGradeChange(tight, distances) + 1e-6);
    }

    private static double maxAdjacentDelta(double[] elevations) {
        double max = 0.0;
        for (int i = 1; i < elevations.length; i++) {
            max = Math.max(max, Math.abs(elevations[i] - elevations[i - 1]));
        }
        return max;
    }

    private static double[] cumulativeChainage(List<Double> segmentDistances) {
        double[] chainage = new double[segmentDistances.size() + 1];
        for (int i = 0; i < segmentDistances.size(); i++) {
            chainage[i + 1] = chainage[i] + segmentDistances.get(i);
        }
        return chainage;
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
