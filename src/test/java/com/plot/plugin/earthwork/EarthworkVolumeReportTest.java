package com.plot.plugin.earthwork;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkVolumeReportTest {

    @Test
    void materialBalanceWhenCutExceedsFillRequirement() {
        EarthworkVolumeReport report = EarthworkVolumeReport.fromMetrics(100L, 80L, 1.1f, 50L, 40L);

        assertEquals(100L, report.geometricCutVolume());
        assertEquals(80L, report.geometricFillVolume());
        assertEquals(88.0, report.requiredFillMaterial(), 1e-6);
        assertEquals(88.0, report.reusableCutVolume(), 1e-6);
        assertEquals(12.0, report.exportVolume(), 1e-6);
        assertEquals(0.0, report.importVolume(), 1e-6);
        assertEquals(50L, report.cutChangedBlocks());
        assertEquals(40L, report.fillChangedBlocks());
        assertEquals(90L, report.totalChangedBlocks());
    }

    @Test
    void materialBalanceWhenFillRequirementExceedsCut() {
        EarthworkVolumeReport report = EarthworkVolumeReport.fromMetrics(60L, 80L, 1.1f, 30L, 20L);

        assertEquals(88.0, report.requiredFillMaterial(), 1e-6);
        assertEquals(60.0, report.reusableCutVolume(), 1e-6);
        assertEquals(0.0, report.exportVolume(), 1e-6);
        assertEquals(28.0, report.importVolume(), 1e-6);
    }

    @Test
    void geometricVolumeIndependentFromChangedBlocks() {
        EarthworkVolumeReport report = EarthworkVolumeReport.fromMetrics(10L, 6L, 1.0f, 0L, 0L);

        assertTrue(report.hasGeometricVolume());
        assertEquals(0L, report.totalChangedBlocks());
    }

    @Test
    void emptyReportHasNoGeometricVolume() {
        EarthworkVolumeReport report = EarthworkVolumeReport.empty();
        assertFalse(report.hasGeometricVolume());
        assertEquals(0L, report.totalChangedBlocks());
    }
}
