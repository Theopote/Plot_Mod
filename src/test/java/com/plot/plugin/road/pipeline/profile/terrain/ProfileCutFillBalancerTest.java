package com.plot.plugin.road.pipeline.profile.terrain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileCutFillBalancerTest {

    @Test
    void raisesProfileWhenFillDominates() {
        List<Integer> ground = List.of(70, 70, 70, 70, 70);
        List<Double> design = List.of(64.0, 64.0, 64.0, 64.0, 64.0);

        List<Double> balanced = ProfileCutFillBalancer.apply(ground, design, 1.1f, 1.0);

        assertTrue(average(balanced) > average(design),
            "fill-heavy profile should be raised toward balance");
        assertTrue(Math.abs(ProfileCutFillBalancer.computeBalanceDiff(
            ground, balanced, 0, com.plot.core.material.MaterialConversionModel.DEFAULT))
            < Math.abs(ProfileCutFillBalancer.computeBalanceDiff(
                ground, design, 0, com.plot.core.material.MaterialConversionModel.DEFAULT)));
    }

    @Test
    void lowersProfileWhenCutDominates() {
        List<Integer> ground = List.of(60, 60, 60, 60, 60);
        List<Double> design = List.of(68.0, 68.0, 68.0, 68.0, 68.0);

        List<Double> balanced = ProfileCutFillBalancer.apply(ground, design, 1.1f, 1.0);

        assertTrue(average(balanced) < average(design),
            "cut-heavy profile should be lowered toward balance");
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

    private static double average(List<Double> elevations) {
        double sum = 0.0;
        for (double elevation : elevations) {
            sum += elevation;
        }
        return sum / elevations.size();
    }
}
