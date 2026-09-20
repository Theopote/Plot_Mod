package com.plot.plugin.powerline.path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClosedPathStationMathTest {

    @Test
    void shortestDistanceWrapsAcrossSeam() {
        assertEquals(15.0, ClosedPathStationMath.distance(185.0, 0.0, 200.0, true), 1e-9);
        assertEquals(15.0, ClosedPathStationMath.distance(0.0, 185.0, 200.0, true), 1e-9);
        assertEquals(10.0, ClosedPathStationMath.distance(5.0, 15.0, 100.0, true), 1e-9);
    }

    @Test
    void forwardDistanceFollowsRouteOrder() {
        assertEquals(15.0, ClosedPathStationMath.forwardDistance(185.0, 0.0, 200.0), 1e-9);
        assertEquals(135.0, ClosedPathStationMath.forwardDistance(50.0, 185.0, 200.0), 1e-9);
        assertEquals(50.0, ClosedPathStationMath.forwardDistance(0.0, 50.0, 200.0), 1e-9);
    }

    @Test
    void openPathUsesLinearDistance() {
        assertEquals(185.0, ClosedPathStationMath.distance(0.0, 185.0, 200.0, false), 1e-9);
    }

    @Test
    void midpointWrapsAcrossSeam() {
        assertEquals(192.5, ClosedPathStationMath.midpoint(185.0, 0.0, 200.0, true), 1e-9);
        assertEquals(192.5, ClosedPathStationMath.midpoint(0.0, 185.0, 200.0, true), 1e-9);
    }

    @Test
    void openPathUsesArithmeticMidpoint() {
        assertEquals(92.5, ClosedPathStationMath.midpoint(0.0, 185.0, 200.0, false), 1e-9);
    }

    @Test
    void normalizeWrapsPerimeter() {
        assertEquals(0.0, ClosedPathStationMath.normalize(200.0, 200.0), 1e-9);
        assertEquals(192.5, ClosedPathStationMath.normalize(192.5, 200.0), 1e-9);
        assertEquals(195.0, ClosedPathStationMath.normalize(-5.0, 200.0), 1e-9);
    }
}
