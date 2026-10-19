package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArcShapeTest {

    @Test
    void getBoundingBoxEnclosesArcEndpoints() {
        ArcShape arc = new ArcShape(new Vec2d(0, 0), 10, 0, Math.PI / 2);

        BoundingBox bounds = arc.getBoundingBox();

        assertEquals(0.0, bounds.getMin().x, 1e-6);
        assertEquals(0.0, bounds.getMin().y, 1e-6);
        assertEquals(10.0, bounds.getMax().x, 1e-6);
        assertEquals(10.0, bounds.getMax().y, 1e-6);
    }

    @Test
    void containsExactlyAcceptsPointOnArc() {
        ArcShape arc = new ArcShape(new Vec2d(0, 0), 10, 0, Math.PI / 2);

        assertTrue(arc.containsExactly(new Vec2d(10, 0), 1e-3));
        assertTrue(arc.containsExactly(new Vec2d(0, 10), 1e-3));
    }

    @Test
    void containsExactlyRejectsPointOutsideAngleRange() {
        ArcShape arc = new ArcShape(new Vec2d(0, 0), 10, 0, Math.PI / 2);

        assertFalse(arc.containsExactly(new Vec2d(-10, 0), 1e-3));
        assertFalse(arc.containsExactly(new Vec2d(0, -10), 1e-3));
    }
}
