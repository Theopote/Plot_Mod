package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureGeometry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TowerStructureTransformTest {

    @Test
    void towerRotatesWithPoleFrame() {
        PoleFrame eastWest = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), 64);
        PoleFrame northSouth = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(0, 1), 64);

        TowerStructureTransform east = new TowerStructureTransform(eastWest, null);
        TowerStructureTransform north = new TowerStructureTransform(northSouth, null);

        TowerStation station = new TowerStation("s", 0, 3, 2);
        TowerLocalPoint corner = TowerStructureGeometry.cornerPoint(station, 1);

        double[] eastWorld = east.toWorld(corner);
        double[] northWorld = north.toWorld(corner);

        assertEquals(-2.0, eastWorld[0], 1e-6);
        assertEquals(64.0, eastWorld[1], 1e-6);
        assertEquals(3.0, eastWorld[2], 1e-6);

        assertEquals(-3.0, northWorld[0], 1e-6);
        assertEquals(64.0, northWorld[1], 1e-6);
        assertEquals(-2.0, northWorld[2], 1e-6);
    }

    @Test
    void halfWidthUsesRightAxis() {
        PoleFrame frame = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), 64);
        TowerStructureTransform transform = new TowerStructureTransform(frame, null);
        double[] world = transform.toWorld(5, 10, 0);
        assertEquals(0.0, world[0], 1e-6);
        assertEquals(74.0, world[1], 1e-6);
        assertEquals(5.0, world[2], 1e-6);
    }

    @Test
    void halfDepthUsesForwardAxis() {
        PoleFrame frame = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), 64);
        TowerStructureTransform transform = new TowerStructureTransform(frame, null);
        double[] world = transform.toWorld(0, 10, 4);
        assertEquals(4.0, world[0], 1e-6);
        assertEquals(74.0, world[1], 1e-6);
        assertEquals(0.0, world[2], 1e-6);
    }

    @Test
    void towerAt45DegreesRemainsSymmetric() {
        PoleFrame frame = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 1), 64);
        TowerStructureTransform transform = new TowerStructureTransform(frame, null);
        double[] left = transform.toWorld(-3, 0, 0);
        double[] right = transform.toWorld(3, 0, 0);
        assertEquals(left[1], right[1], 1e-6);
        double lateralDistance = Math.hypot(right[0] - left[0], right[2] - left[2]);
        assertEquals(6.0, lateralDistance, 1e-3);
    }
}
