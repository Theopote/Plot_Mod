package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationShapeTest {

    private static AnnotationShape distanceAnnotation() {
        return AnnotationShape.createDistanceAnnotation(
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                "10.00");
    }

    @Test
    void getBoundingBoxWrapsDistanceAnnotationGeometry() {
        AnnotationShape annotation = distanceAnnotation();

        BoundingBox bounds = annotation.getBoundingBox();

        assertEquals(0.0, bounds.getMin().x, 1e-6);
        assertEquals(0.0, bounds.getMin().y, 1e-6);
        assertTrue(bounds.getMax().x >= 10.0);
        assertTrue(bounds.getMax().y >= 0.0);
    }

    @Test
    void containsPointHitsDistanceDimensionLine() {
        AnnotationShape annotation = distanceAnnotation();

        assertTrue(annotation.containsPoint(new Vec2d(5, 0), 5.0));
    }

    @Test
    void containsPointMissesPointAwayFromDimensionLine() {
        AnnotationShape annotation = distanceAnnotation();

        assertFalse(annotation.containsPoint(new Vec2d(5, 20), 1.0));
    }

    @Test
    void containsHitsPointOnDistanceLine() {
        AnnotationShape annotation = distanceAnnotation();

        assertTrue(annotation.contains(new Vec2d(5, 0)));
    }
}
