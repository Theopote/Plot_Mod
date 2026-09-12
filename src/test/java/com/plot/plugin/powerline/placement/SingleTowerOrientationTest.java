package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SingleTowerOrientationTest {

    @Test
    void quadrantsMapToWorldAxes() {
        assertEquals(new Vec2d(1, 0), SingleTowerOrientation.tangentForQuadrant(0));
        assertEquals(new Vec2d(0, 1), SingleTowerOrientation.tangentForQuadrant(1));
        assertEquals(new Vec2d(-1, 0), SingleTowerOrientation.tangentForQuadrant(2));
        assertEquals(new Vec2d(0, -1), SingleTowerOrientation.tangentForQuadrant(3));
    }

    @Test
    void rotationWrapsEveryFourSteps() {
        int quadrant = 0;
        for (int i = 0; i < 4; i++) {
            quadrant = SingleTowerOrientation.rotateClockwise(quadrant);
        }
        assertEquals(0, quadrant);
    }

    @Test
    void wheelDeltaRotatesOppositeDirections() {
        assertEquals(1, SingleTowerOrientation.fromWheelDelta(0, 1f));
        assertEquals(3, SingleTowerOrientation.fromWheelDelta(0, -1f));
    }
}
