package com.plot.plugin.road.pipeline.profile.terrain;

import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.road.pipeline.profile.ProfileSolveResult;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shared assertions for FIT_TERRAIN design/build profile invariants. */
final class FitTerrainProfileInvariants {

    private static final double EPSILON = 1e-6;

    private FitTerrainProfileInvariants() {
    }

    static void assertFiniteProfile(List<Double> elevations) {
        for (int i = 0; i < elevations.size(); i++) {
            double value = elevations.get(i);
            assertTrue(Double.isFinite(value),
                "station " + i + " must be finite, got " + value);
        }
    }

    static double maxAbsoluteSegmentGrade(
            List<Double> elevations,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents) {
        double maxGrade = 0.0;
        for (int i = 0; i < segmentDistances.size(); i++) {
            double limit = maxSlopePercents.get(i);
            double grade = Math.abs(GradeLimitedProfileSolver.gradeAtSegment(
                elevations.get(i), elevations.get(i + 1), segmentDistances.get(i)));
            maxGrade = Math.max(maxGrade, grade);
            assertTrue(grade <= limit + EPSILON,
                "segment " + i + " grade " + grade + " exceeds limit " + limit);
        }
        return maxGrade;
    }

    static double maxAdjacentGradeChange(
            List<Double> elevations,
            List<Double> segmentDistances) {
        if (elevations.size() < 3 || segmentDistances.isEmpty()) {
            return 0.0;
        }
        double maxChange = 0.0;
        for (int i = 1; i < elevations.size() - 1; i++) {
            double leftGrade = GradeLimitedProfileSolver.gradeAtSegment(
                elevations.get(i - 1), elevations.get(i), segmentDistances.get(i - 1));
            double rightGrade = GradeLimitedProfileSolver.gradeAtSegment(
                elevations.get(i), elevations.get(i + 1), segmentDistances.get(i));
            maxChange = Math.max(maxChange, Math.abs(rightGrade - leftGrade));
        }
        return maxChange;
    }

    static void assertDesignProfileInvariants(
            List<Double> design,
            List<Integer> ground,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            TerrainFollowPreset preset,
            Integer manualStartHeight,
            Integer manualEndHeight,
            boolean manualEndpointsFeasible) {
        assertFiniteProfile(design);
        maxAbsoluteSegmentGrade(design, segmentDistances, maxSlopePercents);
        double maxGradeChange = maxAdjacentGradeChange(design, segmentDistances);
        assertTrue(maxGradeChange <= preset.maxGradeChangePercent() + EPSILON,
            () -> "max adjacent grade change " + maxGradeChange
                + " exceeds cap " + preset.maxGradeChangePercent());

        if (manualStartHeight != null) {
            assertEquals(manualStartHeight.doubleValue(), design.getFirst(), EPSILON);
        }
        if (manualEndHeight != null && manualEndpointsFeasible) {
            assertEquals(manualEndHeight.doubleValue(), design.getLast(), EPSILON);
        }
    }

    static long cutFillImbalance(
            List<Integer> ground,
            List<Double> design,
            float fillFactor) {
        return ProfileCutFillBalancer.computeBalanceDiff(
            ground,
            design,
            0,
            MaterialConversionModel.fromLegacyFillFactor(fillFactor));
    }

    static List<Double> segmentDistancesFromCumulative(List<Double> cumulativeDistances) {
        List<Double> segmentDistances = new ArrayList<>();
        for (int i = 1; i < cumulativeDistances.size(); i++) {
            segmentDistances.add(cumulativeDistances.get(i) - cumulativeDistances.get(i - 1));
        }
        return segmentDistances;
    }

    static void assertBuildRasterizationReasonable(
            List<Double> design,
            List<Integer> buildHeights) {
        double deviation = maxDesignBuildDeviation(design, buildHeights);
        assertTrue(deviation <= 2.0 + EPSILON,
            "design/build deviation should stay reasonable for voxel rasterization, got " + deviation);
        for (int build : buildHeights) {
            assertTrue(Double.isFinite(build), "build height must be finite");
        }
    }

    static void assertWaterClearanceInvariants(
            List<Double> design,
            List<Integer> waterHeights,
            int clearanceBlocks) {
        if (waterHeights == null || waterHeights.isEmpty()) {
            return;
        }
        int count = Math.min(design.size(), waterHeights.size());
        for (int i = 0; i < count; i++) {
            Integer water = waterHeights.get(i);
            if (water == null) {
                continue;
            }
            assertTrue(
                design.get(i) >= water + clearanceBlocks - EPSILON,
                "station " + i + " design " + design.get(i)
                    + " must stay at or above water " + water + " + clearance " + clearanceBlocks);
        }
    }

    static void assertFullProfileInvariants(
            ProfileSolveResult result,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            TerrainFollowPreset preset,
            Integer manualStartHeight,
            Integer manualEndHeight,
            float fillFactor,
            int waterClearanceBlocks) {
        assertDesignProfileInvariants(
            result.profileDesignElevations(),
            result.profileGroundHeights(),
            segmentDistances,
            maxSlopePercents,
            preset,
            manualStartHeight,
            manualEndHeight,
            result.manualEndpointConstraintFeasible());
        assertBuildRasterizationReasonable(
            result.profileDesignElevations(),
            result.profileBuildHeights());
        assertWaterClearanceInvariants(
            result.profileDesignElevations(),
            result.profileWaterHeights(),
            waterClearanceBlocks);
    }

    private static double maxDesignBuildDeviation(List<Double> design, List<Integer> buildHeights) {
        double max = 0.0;
        int count = Math.min(design.size(), buildHeights.size());
        for (int i = 0; i < count; i++) {
            max = Math.max(max, Math.abs(design.get(i) - buildHeights.get(i)));
        }
        return max;
    }
}
