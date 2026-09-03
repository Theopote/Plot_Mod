package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.core.material.MaterialConversionModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkVolumeReportTest {

    private static final MaterialConversionModel LEGACY_LIKE =
        MaterialConversionModel.fromLegacyFillFactor(1.1f);

    @Test
    void materialBalanceWhenCutExceedsFillRequirement() {
        EarthworkVolumeReport report = EarthworkVolumeReport.fromMetrics(100L, 80L, LEGACY_LIKE, 50L, 40L);

        assertEquals(100L, report.geometricCutVolume());
        assertEquals(80L, report.geometricFillVolume());
        assertEquals(100.0, report.reusableCutVolume(), 1e-6);
        assertEquals(100.0 / 1.1, report.compactedFillSupply(), 0.01);
        assertEquals(80.0, report.compactedFillDemand(), 1e-6);
        assertTrue(report.compactedFillSurplus() > 0.0);
        assertEquals(12.0, report.exportVolume(), 0.01);
        assertEquals(0.0, report.importVolume(), 0.01);
        assertEquals(50L, report.cutChangedBlocks());
        assertEquals(40L, report.fillChangedBlocks());
        assertEquals(90L, report.totalChangedBlocks());
    }

    @Test
    void materialBalanceWhenFillRequirementExceedsCut() {
        EarthworkVolumeReport report = EarthworkVolumeReport.fromMetrics(60L, 80L, LEGACY_LIKE, 30L, 20L);

        assertEquals(80.0, report.compactedFillDemand(), 1e-6);
        assertEquals(60.0, report.reusableCutVolume(), 1e-6);
        assertEquals(0.0, report.exportVolume(), 1e-6);
        assertTrue(report.importVolume() > 0.0);
    }

    @Test
    void defaultMaterialPropertiesMatchUserExample() {
        EarthworkVolumeReport report = EarthworkVolumeReport.fromMetrics(
            1000L, 828L, MaterialConversionModel.DEFAULT, 0L, 0L);

        assertEquals(900.0, report.reusableCutVolume(), 1e-6);
        assertEquals(828.0, report.compactedFillSupply(), 1e-4);
        assertEquals(0.0, report.compactedFillSurplus(), 0.05);
        assertEquals(0.0, report.compactedFillDeficit(), 0.05);
        assertEquals(1000L, report.geometricCutForCompactedTransfer(828L));
        assertEquals(0.0, report.importVolume(), 0.05);
        assertEquals(0.0, report.exportVolume(), 0.05);
    }

    @Test
    void geometricVolumeIndependentFromChangedBlocks() {
        EarthworkVolumeReport report = EarthworkVolumeReport.fromMetrics(
            10L, 6L, new MaterialConversionModel(1.0f, 1.0f), 0L, 0L);

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
