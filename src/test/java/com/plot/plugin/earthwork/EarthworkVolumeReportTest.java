package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Minecraft 1:1 方块统计：仅几何挖填与改方块数。 */
class EarthworkVolumeReportTest {

    @Test
    void geometricAndChangedBlocksWhenCutExceedsFill() {
        EarthworkVolumeReport report = EarthworkVolumeReport.fromMetrics(100L, 80L, 50L, 40L);

        assertEquals(100L, report.geometricCutVolume());
        assertEquals(80L, report.geometricFillVolume());
        assertEquals(20L, report.netGeometricVolume());
        assertEquals(50L, report.cutChangedBlocks());
        assertEquals(40L, report.fillChangedBlocks());
        assertEquals(90L, report.totalChangedBlocks());
    }

    @Test
    void netGeometricWhenFillExceedsCut() {
        EarthworkVolumeReport report = EarthworkVolumeReport.fromMetrics(60L, 80L, 30L, 20L);

        assertEquals(-20L, report.netGeometricVolume());
    }

    @Test
    void geometricVolumeIndependentFromChangedBlocks() {
        EarthworkVolumeReport report = EarthworkVolumeReport.fromMetrics(10L, 6L, 0L, 0L);

        assertTrue(report.hasGeometricVolume());
        assertEquals(0L, report.totalChangedBlocks());
    }

    @Test
    void withChangedBlocksPreservesGeometricVolumes() {
        EarthworkVolumeReport report = EarthworkVolumeReport.fromMetrics(10L, 6L, 0L, 0L)
            .withChangedBlocks(4L, 2L);

        assertEquals(10L, report.geometricCutVolume());
        assertEquals(6L, report.geometricFillVolume());
        assertEquals(6L, report.totalChangedBlocks());
    }

    @Test
    void emptyReportHasNoGeometricVolume() {
        EarthworkVolumeReport report = EarthworkVolumeReport.empty();
        assertFalse(report.hasGeometricVolume());
        assertEquals(0L, report.totalChangedBlocks());
    }
}
