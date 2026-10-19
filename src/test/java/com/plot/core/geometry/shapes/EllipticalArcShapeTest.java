package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EllipticalArcShapeTest {

    private static EllipticalArcShape quarterArc() {
        return new EllipticalArcShape(new Vec2d(0, 0), 10, 10, 0, 0, Math.PI / 2);
    }

    @Test
    void getBoundingBoxEnclosesArcEndpoints() {
        EllipticalArcShape arc = quarterArc();

        BoundingBox bounds = arc.getBoundingBox();

        assertEquals(0.0, bounds.getMin().x, 1e-6);
        assertEquals(0.0, bounds.getMin().y, 1e-6);
        assertEquals(10.0, bounds.getMax().x, 1e-6);
        assertEquals(10.0, bounds.getMax().y, 1e-6);
    }

    @Test
    void getDistanceToPointIsSmallNearArcEndpoint() {
        EllipticalArcShape arc = quarterArc();

        assertTrue(arc.getDistanceToPoint(new Vec2d(10, 0)) < 1.0);
        assertTrue(arc.getDistanceToPoint(new Vec2d(0, 10)) < 1.0);
    }

    @Test
    void getDistanceToPointIsLargeFarFromArc() {
        EllipticalArcShape arc = quarterArc();

        assertTrue(arc.getDistanceToPoint(new Vec2d(-10, 0)) > 5.0);
    }

    @Test
    void containsAcceptsPointInsideArcSweep() {
        EllipticalArcShape arc = quarterArc();

        assertTrue(arc.contains(new Vec2d(7, 0)));
    }

    @Test
    void containsRejectsPointOutsideAngleRange() {
        EllipticalArcShape arc = quarterArc();

        assertFalse(arc.contains(new Vec2d(-7, 0)));
    }
}
