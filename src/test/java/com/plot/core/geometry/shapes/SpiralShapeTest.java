package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpiralShapeTest {

    private static SpiralShape linearSpiral() {
        SpiralShape spiral = new SpiralShape(new Vec2d(0, 0), 10, 2, 20, SpiralType.LINEAR);
        spiral.setStartRadius(10);
        return spiral;
    }

    @Test
    void getBoundingBoxEnclosesSpiralExtent() {
        SpiralShape spiral = linearSpiral();

        BoundingBox bounds = spiral.getBoundingBox();

        assertTrue(bounds.getMin().x <= -40.0);
        assertTrue(bounds.getMin().y <= -40.0);
        assertTrue(bounds.getMax().x >= 40.0);
        assertTrue(bounds.getMax().y >= 40.0);
    }

    @Test
    void containsHitsPointOnSpiralAtStartRadius() {
        SpiralShape spiral = linearSpiral();

        assertTrue(spiral.contains(new Vec2d(10, 0)));
    }

    @Test
    void containsMissesPointFarFromSpiral() {
        SpiralShape spiral = linearSpiral();

        assertFalse(spiral.contains(new Vec2d(10, 100)));
    }

    @Test
    void getDistanceToPointIsSmallNearSpiralStart() {
        SpiralShape spiral = linearSpiral();

        assertTrue(spiral.getDistanceToPoint(new Vec2d(10, 0)) <= 20.0);
    }
}
