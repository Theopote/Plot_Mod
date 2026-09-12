package com.plot.plugin.powerline.path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClosedPathGeometryTest {

    @Test
    void stationDistanceWrapsAcrossSeam() {
        assertEquals(2.0, ClosedPathGeometry.stationDistance(1.0, 99.0, 100.0), 1e-9);
        assertEquals(0.0, ClosedPathGeometry.stationDistance(0.0, 100.0, 100.0), 1e-9);
        assertEquals(10.0, ClosedPathGeometry.stationDistance(5.0, 15.0, 100.0), 1e-9);
    }

    @Test
    void stationsWithinToleranceUsesCyclicDistance() {
        assertTrue(ClosedPathGeometry.stationsWithinTolerance(1.0, 99.0, 100.0, 2.0));
        assertFalse(ClosedPathGeometry.stationsWithinTolerance(1.0, 50.0, 100.0, 2.0));
    }
}
