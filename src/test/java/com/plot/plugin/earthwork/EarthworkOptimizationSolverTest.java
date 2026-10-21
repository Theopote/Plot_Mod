package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.solver.EarthworkOptimizationSolver;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkOptimizationSolverTest {

    @Test
    void computeGeometricZoneOffsetsFromCutFillIntent() {
        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        byZone.put("a", report(10_000L, 0L));
        byZone.put("b", report(0L, 6_000L));
        byZone.put("c", report(0L, 3_000L));

        Map<String, Integer> cellCounts = Map.of("a", 1000, "b", 600, "c", 300);
        Map<String, Integer> offsets = EarthworkOptimizationSolver.computeGeometricZoneOffsets(
            byZone, cellCounts, null);

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

        EarthworkOptimizationSolver.applyZoneOffsets(grid, Map.of("cut", 2, "fill", -3));

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

        SiteEarthworkReport report = EarthworkOptimizationSolver.collectZoneVolumes(grid);
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

        Map<String, Integer> uniform = EarthworkOptimizationSolver.computeGeometricZoneOffsets(
            byZone, Map.of("a", 100), null);
        Map<String, Integer> weighted = EarthworkOptimizationSolver.computeGeometricZoneOffsets(
            byZone,
            Map.of("a", 100),
            EarthworkOptimizationSolver.collectSamplesByZone(grid));

        assertEquals(6, uniform.get("a"));
        assertEquals(5, weighted.get("a"));
    }

    @Test
    void geometricOffsetClampsBeyondMax() {
        assertEquals(
            EarthworkOptimizationSolver.MAX_GEOMETRIC_ZONE_OFFSET,
            EarthworkOptimizationSolver.clampGeometricOffset(10_000L));
        assertEquals(
            -EarthworkOptimizationSolver.MAX_GEOMETRIC_ZONE_OFFSET,
            EarthworkOptimizationSolver.clampGeometricOffset(-10_000L));
        assertEquals(12, EarthworkOptimizationSolver.clampGeometricOffset(12L));
    }

    private static EarthworkVolumeReport report(long cut, long fill) {
        return EarthworkVolumeReport.fromMetrics(cut, fill, 0L, 0L);
    }
}
