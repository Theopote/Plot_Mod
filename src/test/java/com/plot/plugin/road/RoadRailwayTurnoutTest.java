package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadRailwayTurnoutTest {

    @Test
    void resolveThroughPairPicksOppositeApproaches() {
        // N / E / S — through should be N-S (indices 0 and 2)
        List<Vec2d> outwards = List.of(
            new Vec2d(0, 1),
            new Vec2d(1, 0),
            new Vec2d(0, -1));
        int[] pair = RoadRailwayTurnout.resolveThroughPairIndices(outwards).orElseThrow();
        assertArrayEquals(new int[] {0, 2}, pair);
    }

    @Test
    void throughPowerBandUsesJunctionRadius() {
        Vec2d center = new Vec2d(0, 0);
        assertTrue(RoadRailwayTurnout.inThroughPowerBand(new Vec2d(0, 1), center, 3.0));
        assertFalse(RoadRailwayTurnout.inThroughPowerBand(new Vec2d(0, 10), center, 3.0));
    }
}
