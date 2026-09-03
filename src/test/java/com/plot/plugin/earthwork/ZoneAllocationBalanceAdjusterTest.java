package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.solver.EarthworkAllocationMatrix;
import com.plot.plugin.earthwork.solver.ZoneAllocationBalanceAdjuster;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;
import com.plot.core.material.MaterialConversionModel;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneAllocationBalanceAdjusterTest {

    @Test
    void computeZoneOffsetsFromAllocationMatrix() {
        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        byZone.put("a", report(10_000L, 0L));
        byZone.put("b", report(0L, 6_000L));
        byZone.put("c", report(0L, 3_000L));

        EarthworkAllocationMatrix matrix = EarthworkAllocationMatrix.fromZoneReports(byZone, null);
        Map<String, Integer> cellCounts = Map.of("a", 1000, "b", 600, "c", 300);
        Map<String, Integer> offsets = ZoneAllocationBalanceAdjuster.computeZoneOffsets(matrix, cellCounts);

        assertEquals(10, offsets.get("a"));
        assertEquals(-10, offsets.get("b"));
        assertEquals(-10, offsets.get("c"));
    }

    @Test
    void applyZoneOffsetsAdjustsTargetsPerZone() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        DesignTerrainCell cutCell = new DesignTerrainCell(1, 1, new com.plot.api.geometry.Vec2d(1, 1), 70);
        cutCell.setTargetY(60);
        cutCell.setZoneId("cut");
        DesignTerrainCell fillCell = new DesignTerrainCell(2, 2, new com.plot.api.geometry.Vec2d(2, 2), 60);
        fillCell.setTargetY(70);
        fillCell.setZoneId("fill");
        grid.put(1, 1, cutCell);
        grid.put(2, 2, fillCell);

        ZoneAllocationBalanceAdjuster.applyZoneOffsets(grid, Map.of("cut", 2, "fill", -3));

        assertEquals(62, grid.get(1, 1).targetY());
        assertEquals(67, grid.get(2, 2).targetY());
    }

    @Test
    void collectZoneVolumesAggregatesByZone() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        DesignTerrainCell cutCell = new DesignTerrainCell(1, 1, new com.plot.api.geometry.Vec2d(1, 1), 70);
        cutCell.setTargetY(60);
        cutCell.setZoneId("a");
        DesignTerrainCell fillCell = new DesignTerrainCell(2, 2, new com.plot.api.geometry.Vec2d(2, 2), 60);
        fillCell.setTargetY(70);
        fillCell.setZoneId("b");
        grid.put(1, 1, cutCell);
        grid.put(2, 2, fillCell);

        SiteEarthworkReport report = ZoneAllocationBalanceAdjuster.collectZoneVolumes(grid);
        assertEquals(10L, report.zoneReport("a").geometricCutVolume());
        assertEquals(10L, report.zoneReport("b").geometricFillVolume());
        assertTrue(report.totals().hasGeometricVolume());
    }

    @Test
    void weightedOffsetBeatsUniformRoundingWhenSamplesProvided() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        for (int i = 0; i < 100; i++) {
            DesignTerrainCell cell = new DesignTerrainCell(i, 0,
                new com.plot.api.geometry.Vec2d(i + 0.5, 0.5), 70);
            cell.setTargetY(60);
            cell.setZoneId("a");
            grid.put(i, 0, cell);
        }

        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        byZone.put("a", report(550L, 0L));
        byZone.put("b", report(0L, 330L));
        byZone.put("c", report(0L, 220L));
        EarthworkAllocationMatrix matrix = EarthworkAllocationMatrix.fromZoneReports(byZone, null);

        Map<String, Integer> uniform = ZoneAllocationBalanceAdjuster.computeZoneOffsets(
            matrix, Map.of("a", 100, "b", 60, "c", 40));
        Map<String, Integer> weighted = ZoneAllocationBalanceAdjuster.computeZoneOffsets(
            matrix,
            Map.of("a", 100, "b", 60, "c", 40),
            ZoneAllocationBalanceAdjuster.collectSamplesByZone(grid),
            MaterialConversionModel.DEFAULT);

        assertEquals(6, uniform.get("a"));
        assertEquals(5, weighted.get("a"));
    }

    @Test
    void zoneOffsetsConvertCompactedFillBackToGeometricCut() {
        MaterialConversionModel halfReuse = new MaterialConversionModel(0.50f, 1.0f);
        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        byZone.put("a", report(10_000L, 0L, halfReuse));
        byZone.put("b", report(0L, 5_000L, halfReuse));

        EarthworkAllocationMatrix matrix = EarthworkAllocationMatrix.fromZoneReports(byZone, null);
        Map<String, Integer> offsets = ZoneAllocationBalanceAdjuster.computeZoneOffsets(
            matrix,
            Map.of("a", 1000, "b", 500),
            null,
            null,
            byZone,
            halfReuse);

        assertEquals(10, offsets.get("a"));
        assertEquals(-10, offsets.get("b"));
    }

    private static EarthworkVolumeReport report(long cut, long fill) {
        return report(cut, fill, MaterialConversionModel.DEFAULT);
    }

    private static EarthworkVolumeReport report(long cut, long fill, MaterialConversionModel materials) {
        return EarthworkVolumeReport.fromMetrics(cut, fill, materials, 0L, 0L);
    }
}
