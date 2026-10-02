package com.plot.plugin.road.pipeline.profile.terrain;

import com.plot.core.material.MaterialConversionModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileCutFillBalancerTest {

    private static final double EPSILON = 1e-9;

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

    @ParameterizedTest
    @CsvSource({
        "20, 70, 64.0, 1.0",
        "20, 60, 68.0, 0.0",
        "-20, 70, 64.0, 0.0",
        "-20, 60, 68.0, -1.0"
    })
    void cutFillNudgeDirectionUsesQuadrantSemantics(
            long imbalance,
            int ground,
            double design,
            double expectedDirection) {
        assertEquals(
            expectedDirection,
            GradeLimitedProfileSolver.cutFillNudgeDirection(imbalance, ground, design),
            EPSILON);
    }

    @Test
    void cutFillTargetNeverMovesMoreThanConfiguredStep() {
        double target = GradeLimitedProfileSolver.cutFillBalanceTarget(-20L, 70, 64.0, 1.0);

        assertEquals(64.0, target, EPSILON,
            "fill surplus should not nudge cut stations");
        assertTrue(
            Math.abs(target - 64.0) <= GradeLimitedProfileSolver.CUT_FILL_STEP_BLOCKS + EPSILON);
    }

    @Test
    void cutFillTargetDoesNotJumpToGroundOnFillSurplusCutStation() {
        double target = GradeLimitedProfileSolver.cutFillBalanceTarget(-20L, 70, 64.0, 1.0);

        assertEquals(64.0, target, EPSILON);
        assertTrue(target < 70.0, "target must not snap to ground in one step");
    }

    @ParameterizedTest
    @CsvSource({
        "20, 70, 64.0, 1.0",
        "20, 60, 68.0, 1.0",
        "-20, 70, 64.0, 1.0",
        "-20, 60, 68.0, 1.0",
        "20, 70, 64.0, 0.5",
        "-20, 60, 68.0, 0.25"
    })
    void cutFillTargetRespectsMaxStepInvariant(
            long imbalance,
            int ground,
            double design,
            double stepScale) {
        double target = GradeLimitedProfileSolver.cutFillBalanceTarget(
            imbalance, ground, design, stepScale);
        double maxStep = GradeLimitedProfileSolver.CUT_FILL_STEP_BLOCKS * stepScale;

        assertTrue(
            Math.abs(target - design) <= maxStep + EPSILON,
            () -> "target " + target + " moved more than " + maxStep + " from design " + design);
    }
}
