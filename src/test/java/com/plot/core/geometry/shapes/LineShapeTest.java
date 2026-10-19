package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineShapeTest {

    @Test
    void getBoundingBoxMatchesEndpointExtents() {
        LineShape line = new LineShape(new Vec2d(2, -1), new Vec2d(8, 5));

        BoundingBox bounds = line.getBoundingBox();

        assertEquals(2.0, bounds.getMin().x, 1e-6);
        assertEquals(-1.0, bounds.getMin().y, 1e-6);
        assertEquals(8.0, bounds.getMax().x, 1e-6);
        assertEquals(5.0, bounds.getMax().y, 1e-6);
    }

    @Test
    void containsPointHitsNearMidpoint() {
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));

        assertTrue(line.containsPoint(new Vec2d(5, 0), 0.5));
        assertTrue(line.containsPoint(new Vec2d(5, 0.4), 0.5));
    }

    @Test
    void containsPointMissesPointBeyondSegment() {
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));

        assertFalse(line.containsPoint(new Vec2d(12, 0), 0.5));
        assertFalse(line.containsPoint(new Vec2d(5, 5), 0.5));
    }
}
