package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PoleFrameTest {

    @Test
    void straightLineProducesExpectedFrame() {
        PoleFrame frame = PoleFrame.fromPole(new Vec2d(10, 5), new Vec2d(20, 0), 64);
        assertEquals(10.0, frame.origin().x, 1e-6);
        assertEquals(5.0, frame.origin().y, 1e-6);
        assertEquals(64, frame.groundY());
    }

    @Test
    void ninetyDegreeRotationTransformsLateralOffset() {
        PoleFrame east = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), 64);
        PoleFrame north = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(0, 1), 64);

        Vec2d eastPoint = east.toPlanPoint(3.0, 0.0);
        Vec2d northPoint = north.toPlanPoint(3.0, 0.0);

        assertEquals(0.0, eastPoint.x, 1e-6);
        assertEquals(3.0, eastPoint.y, 1e-6);
        assertEquals(-3.0, northPoint.x, 1e-6);
        assertEquals(0.0, northPoint.y, 1e-6);
    }

    @Test
    void zeroTangentUsesStableFallback() {
        PoleFrame frame = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(0, 0), 64);
        Vec2d point = frame.toPlanPoint(2.0, 1.0);
        assertEquals(1.0, point.x, 1e-6);
        assertEquals(2.0, point.y, 1e-6);
    }
}
