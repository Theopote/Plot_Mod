package com.plot.plugin.road.tunnel;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelProfileTest {

    private static final int ROAD_Y = 64;
    private static final int HALF_W = 3;
    private static final int CLEAR = 6;

    @Test
    void sevenShapesProduceDistinguishableAirSets() {
        TunnelShape[] shapes = TunnelShape.values();
        for (int i = 0; i < shapes.length; i++) {
            Set<Long> a = airSet(shapes[i]);
            assertFalse(a.isEmpty(), shapes[i] + " must have cavity air");
            for (int j = i + 1; j < shapes.length; j++) {
                Set<Long> b = airSet(shapes[j]);
                assertNotEquals(a, b, shapes[i] + " vs " + shapes[j] + " air sets must differ");
                double jaccard = jaccard(a, b);
                assertTrue(jaccard < 0.92,
                    shapes[i] + " vs " + shapes[j] + " too similar, jaccard=" + jaccard);
            }
        }
    }

    @Test
    void dShapeAndArchDifferOnSideWallColumns() {
        // At mid-height near the wall, D_SHAPE stays vertical while ARCH has already dropped.
        int midY = ROAD_Y + CLEAR / 2;
        int nearWall = HALF_W;
        boolean dAir = TunnelProfile.isCavityAir(TunnelShape.D_SHAPE, nearWall, midY, ROAD_Y, HALF_W, CLEAR);
        boolean archAir = TunnelProfile.isCavityAir(TunnelShape.ARCH, nearWall, midY, ROAD_Y, HALF_W, CLEAR);
        assertTrue(dAir || archAir, "at least one shape occupies side mid-height");
        assertNotEquals(dAir, archAir, "D_SHAPE and ARCH must disagree on side-wall mid-height");
    }

    @Test
    void circularAndEllipticalDiffer() {
        assertNotEquals(airSet(TunnelShape.CIRCULAR), airSet(TunnelShape.ELLIPTICAL));
    }

    @Test
    void circularAndEllipticalAllowInvertBelowRoad() {
        assertTrue(TunnelProfile.derivedInvertDepth(TunnelShape.CIRCULAR, HALF_W, CLEAR) > 0);
        assertTrue(TunnelProfile.derivedInvertDepth(TunnelShape.ELLIPTICAL, HALF_W, CLEAR) > 0);
        assertTrue(TunnelProfile.isCavityAir(
            TunnelShape.CIRCULAR, 0, ROAD_Y - 1, ROAD_Y, HALF_W, CLEAR)
            || TunnelProfile.isCavityAir(
            TunnelShape.ELLIPTICAL, 0, ROAD_Y - 1, ROAD_Y, HALF_W, CLEAR),
            "invert shapes must place air below road datum on centerline");
    }

    @Test
    void rectangularHasNoInvert() {
        assertTrue(TunnelProfile.derivedInvertDepth(TunnelShape.RECTANGULAR, HALF_W, CLEAR) == 0);
        assertFalse(TunnelProfile.isCavityAir(
            TunnelShape.RECTANGULAR, 0, ROAD_Y, ROAD_Y, HALF_W, CLEAR));
        assertFalse(TunnelProfile.isCavityAir(
            TunnelShape.RECTANGULAR, 0, ROAD_Y - 1, ROAD_Y, HALF_W, CLEAR));
    }

    @Test
    void centerlineClearanceStaysConnectedAboveRoad() {
        for (TunnelShape shape : TunnelShape.values()) {
            for (int y = ROAD_Y + 1; y <= ROAD_Y + CLEAR; y++) {
                assertTrue(
                    TunnelProfile.isCavityAir(shape, 0, y, ROAD_Y, HALF_W, CLEAR),
                    shape + " must keep centerline air at y=" + y);
            }
        }
    }

    private static Set<Long> airSet(TunnelShape shape) {
        int invert = TunnelProfile.derivedInvertDepth(shape, HALF_W, CLEAR);
        Set<Long> cells = new HashSet<>();
        for (int lat = -HALF_W - 1; lat <= HALF_W + 1; lat++) {
            for (int y = ROAD_Y - invert - 1; y <= ROAD_Y + CLEAR + 2; y++) {
                if (TunnelProfile.isCavityAir(shape, lat, y, ROAD_Y, HALF_W, CLEAR)) {
                    cells.add((((long) lat) << 32) ^ (y & 0xffffffffL));
                }
            }
        }
        return cells;
    }

    private static double jaccard(Set<Long> a, Set<Long> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        int inter = 0;
        for (Long v : a) {
            if (b.contains(v)) {
                inter++;
            }
        }
        int union = a.size() + b.size() - inter;
        return union == 0 ? 1.0 : (double) inter / union;
    }
}
