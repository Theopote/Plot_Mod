package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.core.material.MaterialConversionModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Minecraft 1:1 方块统计：挖填量不经压实/可利用率换算。 */
class EarthworkVolumeReportTest {

    @Test
    void oneToOneWhenCutExceedsFill() {
        EarthworkVolumeReport report = EarthworkVolumeReport.fromMetrics(
            100L, 80L, MaterialConversionModel.LEARNING, 50L, 40L);

        assertEquals(100L, report.geometricCutVolume());
        assertEquals(80L, report.geometricFillVolume());
        assertEquals(100.0, report.reusableCutVolume(), 1e-9);
        assertEquals(100.0, report.compactedFillSupply(), 1e-9);
        assertEquals(80.0, report.compactedFillDemand(), 1e-9);
        assertEquals(20.0, report.exportVolume(), 1e-9);
        assertEquals(0.0, report.importVolume(), 1e-9);
        assertEquals(90L, report.totalChangedBlocks());
    }

    @Test
    void oneToOneWhenFillExceedsCut() {
        EarthworkVolumeReport report = EarthworkVolumeReport.fromMetrics(
            60L, 80L, MaterialConversionModel.LEARNING, 30L, 20L);

        assertEquals(0.0, report.exportVolume(), 1e-9);
        assertEquals(20.0, report.importVolume(), 1e-9);
    }

    @Test
    void conversionModelArgumentDoesNotChangeBalance() {
        EarthworkVolumeReport withLearning = EarthworkVolumeReport.fromMetrics(
            1000L, 828L, MaterialConversionModel.LEARNING, 0L, 0L);
        EarthworkVolumeReport withDefault = EarthworkVolumeReport.fromMetrics(
            1000L, 828L, MaterialConversionModel.DEFAULT, 0L, 0L);

        assertEquals(withDefault.reusableCutVolume(), withLearning.reusableCutVolume(), 1e-9);
        assertEquals(withDefault.exportVolume(), withLearning.exportVolume(), 1e-9);
        assertEquals(172.0, withLearning.exportVolume(), 1e-9);
    }

    @Test
    void geometricVolumeIndependentFromChangedBlocks() {
        EarthworkVolumeReport report = EarthworkVolumeReport.fromMetrics(
            10L, 6L, MaterialConversionModel.DEFAULT, 0L, 0L);

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
