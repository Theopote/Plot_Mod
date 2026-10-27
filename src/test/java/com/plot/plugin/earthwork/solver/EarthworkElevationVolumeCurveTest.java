package com.plot.plugin.earthwork.solver;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkElevationVolumeCurveTest {

    @Test
    void balanceAndMinWorkAreIndependentOnSkewedTerrain() {
        List<EarthworkElevationVolumeCurve.Column> columns = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            columns.add(new EarthworkElevationVolumeCurve.Column(64, 64));
        }
        columns.add(new EarthworkElevationVolumeCurve.Column(74, 64));

        EarthworkElevationVolumeCurve curve = EarthworkElevationVolumeCurve.fromColumns(columns, 64);

        assertEquals(64, curve.minWorkY());
        assertEquals(65, curve.balanceY());
        assertTrue(curve.optimaDiffer());
        assertEquals(10L, curve.sampleAt(64).work());
        assertEquals(0L, curve.sampleAt(65).imbalance());
        assertTrue(curve.sampleAt(65).work() > curve.sampleAt(64).work());
    }

    @Test
    void uniformShiftPreservesSlopedRelativeOffsets() {
        List<EarthworkElevationVolumeCurve.Column> columns = List.of(
            new EarthworkElevationVolumeCurve.Column(60, 64),
            new EarthworkElevationVolumeCurve.Column(62, 63));
        EarthworkElevationVolumeCurve.Sample atRef = EarthworkElevationVolumeCurve.volumeAt(columns, 64, 64);
        EarthworkElevationVolumeCurve.Sample raised = EarthworkElevationVolumeCurve.volumeAt(columns, 64, 65);
        assertEquals(0L, atRef.cut());
        assertEquals(5L, atRef.fill());
        assertEquals(0L, raised.cut());
        assertEquals(7L, raised.fill());
        assertEquals(2L, raised.fill() - atRef.fill());
    }

    @Test
    void sampleAtExactRecomputesBetweenSparseSamples() {
        List<EarthworkElevationVolumeCurve.Column> columns = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            columns.add(new EarthworkElevationVolumeCurve.Column(64 + (i % 50), 64));
        }
        EarthworkElevationVolumeCurve curve = EarthworkElevationVolumeCurve.fromColumns(columns, 64);
        assertTrue(curve.sampleStep() > 1);
        assertFalse(curve.canCompareAdjacentHeights());

        EarthworkElevationVolumeCurve.Sample at65 = curve.sampleAtExact(65);
        assertEquals(65, at65.y());
        assertTrue(at65.work() >= 0L);
    }

    @Test
    void denseCurveAllowsAdjacentHeightComparison() {
        List<EarthworkElevationVolumeCurve.Column> columns = List.of(
            new EarthworkElevationVolumeCurve.Column(64, 64),
            new EarthworkElevationVolumeCurve.Column(66, 64));
        EarthworkElevationVolumeCurve curve = EarthworkElevationVolumeCurve.fromColumns(columns, 64);
        assertEquals(1, curve.sampleStep());
        assertTrue(curve.canCompareAdjacentHeights());
        assertNotNull(curve.sampleAtExact(64));
        assertNotNull(curve.sampleAtExact(65));
    }

    @Test
    void slopeCoupledCurveCannotCompareAdjacentHeights() {
        List<EarthworkElevationVolumeCurve.Sample> samples = List.of(
            new EarthworkElevationVolumeCurve.Sample(64, 10, 5),
            new EarthworkElevationVolumeCurve.Sample(65, 8, 8));
        com.plot.plugin.earthwork.grading.DesignTerrainGrid grid =
            new com.plot.plugin.earthwork.grading.DesignTerrainGrid();
        com.plot.api.geometry.Vec2d center = new com.plot.api.geometry.Vec2d(0.5, 0.5);
        com.plot.plugin.earthwork.grading.DesignTerrainCell cell =
            new com.plot.plugin.earthwork.grading.DesignTerrainCell(0, 0, center, 64);
        cell.setTargetY(64);
        cell.setZoneId("pad");
        grid.put(0, 0, cell);
        EarthworkElevationVolumeCurve curve =
            EarthworkElevationVolumeCurve.fromSlopeCoupledSamples(samples, 64, grid);
        assertFalse(curve.canCompareAdjacentHeights());
    }
}
