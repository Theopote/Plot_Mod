package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolylineShapeTest {

    @Test
    void getBoundingBoxSpansAllVertices() {
        PolylineShape polyline = new PolylineShape(
                List.of(new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 8)),
                false);

        BoundingBox bounds = polyline.getBoundingBox();

        assertEquals(0.0, bounds.getMin().x, 1e-6);
        assertEquals(0.0, bounds.getMin().y, 1e-6);
        assertEquals(10.0, bounds.getMax().x, 1e-6);
        assertEquals(8.0, bounds.getMax().y, 1e-6);
    }

    @Test
    void containsPointHitsSegment() {
        PolylineShape polyline = new PolylineShape(
                List.of(new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 8)),
                false);

        assertTrue(polyline.containsPoint(new Vec2d(5, 0), 0.5));
        assertTrue(polyline.containsPoint(new Vec2d(10, 4), 0.5));
    }

    @Test
    void containsPointMissesGapBetweenSegments() {
        PolylineShape polyline = new PolylineShape(
                List.of(new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 8)),
                false);

        assertFalse(polyline.containsPoint(new Vec2d(5, 5), 0.5));
    }
}
