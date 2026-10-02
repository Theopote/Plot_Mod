package com.plot.plugin.road.pipeline.profile.terrain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileCutFillBalancerTest {

    @Test
    void raisesProfileWhenCutDominates() {
        List<Integer> ground = List.of(70, 70, 70, 70, 70);
        List<Double> design = List.of(64.0, 64.0, 64.0, 64.0, 64.0);

        List<Double> balanced = ProfileCutFillBalancer.apply(ground, design, 1.1f, 1.0);

        assertTrue(average(balanced) > average(design),
            "cut-heavy profile should be raised toward balance");
        assertTrue(Math.abs(ProfileCutFillBalancer.computeBalanceDiff(
            ground, balanced, 0, com.plot.core.material.MaterialConversionModel.DEFAULT))
            < Math.abs(ProfileCutFillBalancer.computeBalanceDiff(
                ground, design, 0, com.plot.core.material.MaterialConversionModel.DEFAULT)));
    }

    @Test
    void lowersProfileWhenFillDominates() {
        List<Integer> ground = List.of(60, 60, 60, 60, 60);
        List<Double> design = List.of(68.0, 68.0, 68.0, 68.0, 68.0);

        List<Double> balanced = ProfileCutFillBalancer.apply(ground, design, 1.1f, 1.0);

        assertTrue(average(balanced) < average(design),
            "fill-heavy profile should be lowered toward balance");
    }

    @Test
    void balanceWeightScalesAppliedOffset() {
        List<Integer> ground = List.of(70, 70, 70, 70, 70);
        List<Double> design = List.of(64.0, 64.0, 64.0, 64.0, 64.0);

        List<Double> half = ProfileCutFillBalancer.apply(ground, design, 1.1f, 0.5);
        List<Double> full = ProfileCutFillBalancer.apply(ground, design, 1.1f, 1.0);

        double halfShift = average(half) - average(design);
        double fullShift = average(full) - average(design);
        assertTrue(halfShift > 0.0);
        assertEquals(fullShift, halfShift * 2.0, 0.75);
    }

    @Test
    void zeroWeightLeavesDesignUnchanged() {
        List<Integer> ground = List.of(70, 70, 70);
        List<Double> design = List.of(64.0, 64.0, 64.0);

        List<Double> unchanged = ProfileCutFillBalancer.apply(ground, design, 1.1f, 0.0);

        assertEquals(design, unchanged);
    }

    @Test
    void skipsBalanceOnLargeTerrainSteps() {
        List<Integer> ground = List.of(60, 60, 60, 75, 75, 75);
        List<Double> design = List.of(64.0, 64.0, 64.0, 68.0, 68.0, 68.0);

        List<Double> unchanged = ProfileCutFillBalancer.apply(ground, design, 1.35f, 1.0);

        assertEquals(design, unchanged);
    }

    @Test
    void presetBalanceWeightsIncreaseFromTightToGentle() {
        assertTrue(TerrainFollowPreset.GENTLE.cutFillBalanceWeight()
            > TerrainFollowPreset.STANDARD.cutFillBalanceWeight());
        assertTrue(TerrainFollowPreset.STANDARD.cutFillBalanceWeight()
            > TerrainFollowPreset.TIGHT.cutFillBalanceWeight());
    }

    @Test
    void balancePreservesLockedStart() {
        List<Double> distances = constantDistances(4, 20.0);
        List<Float> slopes = constantSlopes(4, 8.0f);
        List<Integer> ground = List.of(70, 70, 70, 70, 70);
        List<Double> design = List.of(64.0, 64.0, 64.0, 64.0, 68.0);

        List<Double> balanced = ProfileCutFillBalancer.apply(
            ground, design, distances, slopes, 1.1f, 1.0,
            TerrainFollowPreset.STANDARD, 64, null, true);

        assertEquals(64.0, balanced.getFirst(), 1e-6);
    }

    @Test
    void balancePreservesLockedEnd() {
        List<Double> distances = constantDistances(4, 20.0);
        List<Float> slopes = constantSlopes(4, 8.0f);
        List<Integer> ground = List.of(70, 70, 70, 70, 70);
        List<Double> design = List.of(64.0, 64.0, 64.0, 64.0, 70.0);

        List<Double> balanced = ProfileCutFillBalancer.apply(
            ground, design, distances, slopes, 1.1f, 1.0,
            TerrainFollowPreset.STANDARD, null, 70, true);

        assertEquals(70.0, balanced.getLast(), 1e-6);
    }

    @Test
    void balancePreservesBothEndpoints() {
        List<Double> distances = constantDistances(8, 15.0);
        List<Float> slopes = constantSlopes(8, 8.0f);
        List<Integer> ground = List.of(70, 70, 70, 70, 70, 70, 70, 70, 70);
        List<Double> design = List.of(64.0, 65.0, 66.0, 67.0, 68.0, 69.0, 69.5, 69.8, 70.0);

        List<Double> balanced = ProfileCutFillBalancer.apply(
            ground, design, distances, slopes, 1.1f, 1.0,
            TerrainFollowPreset.STANDARD, 64, 70, true);

        assertEquals(64.0, balanced.getFirst(), 1e-6);
        assertEquals(70.0, balanced.getLast(), 1e-6);
    }

    @Test
    void balanceDoesNotViolateMaxGradeAfterTaper() {
        TerrainFollowPreset preset = TerrainFollowPreset.STANDARD;
        List<Double> distances = constantDistances(9, 12.0);
        List<Float> slopes = constantSlopes(9, 8.0f);
        List<Integer> ground = List.of(70, 70, 70, 70, 70, 70, 70, 70, 70, 70);
        List<Double> design = List.of(
            64.0, 64.5, 65.0, 65.5, 66.0, 66.5, 67.0, 67.5, 68.0, 70.0);

        List<Double> balanced = ProfileCutFillBalancer.apply(
            ground, design, distances, slopes, 1.1f, 1.0,
            preset, 64, 70, true);

        assertEquals(64.0, balanced.getFirst(), 1e-6);
        assertEquals(70.0, balanced.getLast(), 1e-6);

        double maxSlopePercent = maxAbsoluteSegmentGrade(balanced, distances);
        assertTrue(maxSlopePercent <= 8.0 + 1e-6,
            "max segment grade should stay within slope limit, got " + maxSlopePercent);
    }

    private static List<Double> constantDistances(int segmentCount, double distance) {
        List<Double> distances = new ArrayList<>(segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            distances.add(distance);
        }
        return distances;
    }

    private static List<Float> constantSlopes(int segmentCount, float slope) {
        List<Float> slopes = new ArrayList<>(segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            slopes.add(slope);
        }
        return slopes;
    }

    private static double maxAbsoluteSegmentGrade(List<Double> elevations, List<Double> distances) {
        double maxSlopePercent = 0.0;
        for (int i = 0; i < distances.size(); i++) {
            double grade = Math.abs(GradeLimitedProfileSolver.gradeAtSegment(
                elevations.get(i), elevations.get(i + 1), distances.get(i)));
            maxSlopePercent = Math.max(maxSlopePercent, grade);
        }
        return maxSlopePercent;
    }

    private static double average(List<Double> elevations) {
        double sum = 0.0;
        for (double elevation : elevations) {
            sum += elevation;
        }
        return sum / elevations.size();
    }
}
