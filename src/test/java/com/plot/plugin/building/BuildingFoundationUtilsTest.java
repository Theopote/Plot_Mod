package com.plot.plugin.building;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
