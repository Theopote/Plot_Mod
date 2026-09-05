package com.plot.plugin.building;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingFoundationUtilsTest {

    @Test
    void computeBaseElevationUsesModeWhenClear() {
        assertEquals(64, BuildingFoundationUtils.computeBaseElevation(
            List.of(64, 64, 64, 64, 65, 63), null));
    }

    @Test
    void computeBaseElevationUsesHigherValueOnTie() {
        assertEquals(65, BuildingFoundationUtils.computeBaseElevation(
            List.of(64, 64, 65, 65), null));
    }

    @Test
    void computeBaseElevationHonorsManualOverride() {
        assertEquals(70, BuildingFoundationUtils.computeBaseElevation(
            List.of(64, 64, 65, 65), 70));
    }

    @Test
    void computeBaseElevationDefaultsWhenEmpty() {
        assertEquals(64, BuildingFoundationUtils.computeBaseElevation(List.of(), null));
    }

    @Test
    void computeBaseElevationPrefersEarthworkPadOverTerrain() {
        assertEquals(72, BuildingFoundationUtils.computeBaseElevation(
            List.of(64, 64, 65, 65), null, 72));
    }

    @Test
    void computeBalancedElevationPrefersLowerFillCostNearDominant() {
        // tie mode 64/70 → dominant 70, but balanced should move lower
        List<Integer> samples = List.of(64, 64, 64, 70, 70, 70);
        int balanced = BuildingFoundationUtils.computeBalancedElevation(samples);
        assertTrue(balanced <= 70);
        BuildingFoundationUtils.EarthworkEstimate atBalanced =
            BuildingFoundationUtils.estimateEarthwork(samples, balanced);
        BuildingFoundationUtils.EarthworkEstimate at70 =
            BuildingFoundationUtils.estimateEarthwork(samples, 70);
        assertTrue(atBalanced.weightedCost() <= at70.weightedCost() + 1e-9);
    }

    @Test
    void estimateEarthworkComputesCutAndFill() {
        BuildingFoundationUtils.EarthworkEstimate estimate =
            BuildingFoundationUtils.estimateEarthwork(List.of(66, 64, 62), 64);
        assertEquals(2, estimate.cut());
        assertEquals(2, estimate.fill());
        assertEquals(2 * BuildingFoundationUtils.CUT_WEIGHT + 2 * BuildingFoundationUtils.FILL_WEIGHT,
            estimate.weightedCost(), 1e-9);
    }
}
