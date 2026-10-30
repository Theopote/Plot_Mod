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
    void resolveThroughPairAcceptsOpposingYArm() {
        List<Vec2d> y = List.of(
            new Vec2d(1, 0.2).normalize(),
            new Vec2d(1, -0.2).normalize(),
            new Vec2d(-1, 0).normalize());
        assertTrue(
            RoadRailwayTurnout.resolveThroughPairIndices(y).isPresent(),
            "west vs an east arm should qualify as through");
    }

    @Test
    void resolveThroughPairEmptyWhenArmsLackOpposition() {
        // 0° / 40° / 80°：最对向点积仍为正，不强制直股
        List<Vec2d> fan = List.of(
            new Vec2d(1, 0),
            new Vec2d(Math.cos(Math.toRadians(40)), Math.sin(Math.toRadians(40))),
            new Vec2d(Math.cos(Math.toRadians(80)), Math.sin(Math.toRadians(80))));
        assertTrue(RoadRailwayTurnout.resolveThroughPairIndices(fan).isEmpty());

        List<Vec2d> bundle = List.of(
            new Vec2d(1, 0.1).normalize(),
            new Vec2d(1, 0).normalize(),
            new Vec2d(1, -0.1).normalize());
        assertTrue(RoadRailwayTurnout.resolveThroughPairIndices(bundle).isEmpty());
    }

    @Test
    void throughPowerBandUsesJunctionRadius() {
        Vec2d center = new Vec2d(0, 0);
        assertTrue(RoadRailwayTurnout.inThroughPowerBand(new Vec2d(0, 1), center, 3.0));
        assertFalse(RoadRailwayTurnout.inThroughPowerBand(new Vec2d(0, 10), center, 3.0));
    }

    @Test
    void poweredDecorBandExcludesFrogCenter() {
        Vec2d center = new Vec2d(0, 0);
        assertFalse(RoadRailwayTurnout.inPoweredDecorBand(center, center, 4.0));
        assertFalse(RoadRailwayTurnout.inPoweredDecorBand(new Vec2d(0, 1.0), center, 4.0));
        assertFalse(RoadRailwayTurnout.inPoweredDecorBand(new Vec2d(0, 1.5), center, 4.0));
        assertTrue(RoadRailwayTurnout.inPoweredDecorBand(new Vec2d(0, 2.0), center, 4.0));
    }

    @Test
    void detectorMarkIsOffsetFromCenter() {
        Vec2d center = new Vec2d(0, 0);
        Vec2d mark = RoadRailwayTurnout.detectorMarkPosition(
            center, new Vec2d(0, 1), new Vec2d(0, -1)).orElseThrow();
        assertTrue(mark.distanceSquared(center) > 0.5);
        assertTrue(mark.distance(center) < 1.5);
    }

    @Test
    void densifiedThroughPathIsGridConnected() {
        List<Vec2d> armA = List.of(new Vec2d(0, 4), new Vec2d(0, 2), new Vec2d(0, 0));
        List<Vec2d> armB = List.of(new Vec2d(0, -4), new Vec2d(0, -2), new Vec2d(0, 0));
        List<Vec2d> path = RoadRailwayTurnout.densifyThroughPath(armA, armB, new Vec2d(0, 0), 0.5);
        assertTrue(RoadRailwayTurnout.isGridConnectedChain(path));
        assertTrue(path.size() >= 8);
    }
}
