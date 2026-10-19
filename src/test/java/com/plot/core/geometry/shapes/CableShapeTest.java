package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CableShapeTest {

    private static CableShape horizontalCable() {
        return new CableShape(new Vec2d(0, 0), new Vec2d(10, 0), 1.0, 50);
    }

    @Test
    void getBoundingBoxSpansCableEndpoints() {
        CableShape cable = horizontalCable();

        BoundingBox bounds = cable.getBoundingBox();

        assertEquals(0.0, bounds.getMin().x, 1e-6);
        assertEquals(10.0, bounds.getMax().x, 1e-6);
        assertTrue(bounds.getMax().y >= 0.0);
    }

    @Test
    void containsHitsPointNearStart() {
        CableShape cable = horizontalCable();

        assertTrue(cable.contains(new Vec2d(0, 0)));
    }

    @Test
    void containsMissesPointFarFromCable() {
        CableShape cable = horizontalCable();

        assertFalse(cable.contains(new Vec2d(5, 50)));
    }

    @Test
    void getDistanceToPointIsZeroAtStart() {
        CableShape cable = horizontalCable();

        assertEquals(0.0, cable.getDistanceToPoint(new Vec2d(0, 0)), 1e-3);
    }
}
