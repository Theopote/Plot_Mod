package com.plot.plugin.road.pipeline.profile.terrain;

import com.plot.core.material.MaterialConversionModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileCutFillBalancerTest {

    @Test
    void computeBalanceDiffDetectsCutDominance() {
        List<Integer> ground = List.of(70, 70, 70, 70, 70);
        List<Double> design = List.of(64.0, 64.0, 64.0, 64.0, 64.0);

        long imbalance = ProfileCutFillBalancer.computeBalanceDiff(
            ground, design, 0, MaterialConversionModel.DEFAULT);

        assertTrue(imbalance > 0, "cut-heavy profile should report positive supply surplus");
    }

    @Test
    void computeBalanceDiffDetectsFillDominance() {
        List<Integer> ground = List.of(60, 60, 60, 60, 60);
        List<Double> design = List.of(68.0, 68.0, 68.0, 68.0, 68.0);

        long imbalance = ProfileCutFillBalancer.computeBalanceDiff(
            ground, design, 0, MaterialConversionModel.DEFAULT);

        assertTrue(imbalance < 0, "fill-heavy profile should report negative supply surplus");
    }

    @Test
    void findBalancingOffsetRaisesCutHeavyProfile() {
        List<Integer> ground = List.of(70, 70, 70, 70, 70);
        List<Double> design = List.of(64.0, 64.0, 64.0, 64.0, 64.0);

        double offset = ProfileCutFillBalancer.findBalancingOffset(ground, design, 1.1f);

        assertTrue(offset > 0.0, "cut-heavy profile should search for a positive offset");
    }

    @Test
    void presetBalanceWeightsIncreaseFromTightToGentle() {
        assertTrue(TerrainFollowPreset.GENTLE.cutFillBalanceWeight()
            > TerrainFollowPreset.STANDARD.cutFillBalanceWeight());
        assertTrue(TerrainFollowPreset.STANDARD.cutFillBalanceWeight()
            > TerrainFollowPreset.TIGHT.cutFillBalanceWeight());
    }

    @Test
    void cutFillNudgeDirectionRaisesCutStationsWhenSupplyExceedsFill() {
        assertEquals(1.0, GradeLimitedProfileSolver.cutFillNudgeDirection(20L, 70, 64.0), 1e-9);
        assertEquals(-1.0, GradeLimitedProfileSolver.cutFillNudgeDirection(20L, 60, 68.0), 1e-9);
        assertEquals(0.0, GradeLimitedProfileSolver.cutFillNudgeDirection(20L, 64, 64.0), 1e-9);
    }

    @Test
    void cutFillNudgeDirectionLowersStationsWhenFillExceedsSupply() {
        assertEquals(-1.0, GradeLimitedProfileSolver.cutFillNudgeDirection(-20L, 70, 64.0), 1e-9);
        assertEquals(-1.0, GradeLimitedProfileSolver.cutFillNudgeDirection(-20L, 60, 68.0), 1e-9);
    }
}
