package com.plot.plugin.earthwork.solver;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
