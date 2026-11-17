package com.plot.plugin.road.profile;

import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadProfileChartStationDisplayTest {

    @Test
    void stationAxisLabelsUseWorldScaleNotRawCanvas() {
        double canvasTotal = 3000.0;
        double worldTotal = 889.21;
        double scale = RoadProfileChartRenderer.sanitizeStationDisplayScale(worldTotal / canvasTotal);
        String endLabel = RoadStationing.format(canvasTotal * scale, RoadStationFormat.DISTANCE_METERS);
        assertTrue(endLabel.contains("889") || endLabel.contains("890"), endLabel);
        assertFalse(endLabel.contains("km"), endLabel);
        assertEquals("0.00 m", RoadStationing.format(0.0 * scale, RoadStationFormat.DISTANCE_METERS));
    }

    @Test
    void invalidScaleFallsBackToIdentity() {
        assertEquals(1.0, RoadProfileChartRenderer.sanitizeStationDisplayScale(0.0));
        assertEquals(1.0, RoadProfileChartRenderer.sanitizeStationDisplayScale(Double.NaN));
    }
}
