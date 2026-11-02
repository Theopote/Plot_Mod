package com.plot.plugin.road.tunnel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelCrossSectionMaskTest {

    private static final int ROAD_Y = 64;
    private static final int ENVELOPE = 5;
    private static final int SIDE = 1;
    private static final int CLEAR = 6;
    private static final int LINING = 1;

    @Test
    void rectangularMaskHasAirCavityAndLiningShell() {
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            TunnelShape.RECTANGULAR, 3, 1, 5, 1, 64, false);
        assertEquals(TunnelCrossSectionMask.CellKind.AIR, mask.kindAt(0, 69));
        assertEquals(TunnelCrossSectionMask.CellKind.LINING, mask.kindAt(0, 70));
        assertEquals(TunnelCrossSectionMask.CellKind.OUTSIDE, mask.kindAt(0, 64));
    }

    @Test
    void portalFrameThickensLining() {
        TunnelCrossSectionMask normal = TunnelCrossSectionMask.build(
            TunnelShape.RECTANGULAR, 3, 1, 5, 1, 64, false);
        TunnelCrossSectionMask portal = TunnelCrossSectionMask.build(
            TunnelShape.RECTANGULAR, 3, 1, 5, 1, 64, true);
        int[] normalLining = {0};
        int[] portalLining = {0};
        normal.forEach((l, y, kind) -> {
            if (kind == TunnelCrossSectionMask.CellKind.LINING) {
                normalLining[0]++;
            }
        });
        portal.forEach((l, y, kind) -> {
            if (kind == TunnelCrossSectionMask.CellKind.LINING) {
                portalLining[0]++;
            }
        });
        assertTrue(portalLining[0] > normalLining[0]);
    }

    @Test
    void liningFollowsArchInsteadOfFilledRectangle() {
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            TunnelShape.ARCH, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y, false);
        // Near the edge at crown height, a rectangular shell would still be LINING;
        // a true arch shell should leave that cell OUTSIDE.
        int cavityHalf = (ENVELOPE + SIDE * 2) / 2;
        int crownY = ROAD_Y + CLEAR + LINING;
        assertEquals(
            TunnelCrossSectionMask.CellKind.OUTSIDE,
            mask.kindAt(cavityHalf, crownY),
            "arch must not fill a rectangular corner above the sloping roof");
    }

    @Test
    void circularInvertExistsBelowRoadWithoutCarvingPavement() {
        // Clear height below diameter so the true circle dips under the road datum.
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            TunnelShape.CIRCULAR, ENVELOPE, SIDE, 4, LINING, ROAD_Y, false);
        assertEquals(TunnelCrossSectionMask.CellKind.OUTSIDE, mask.kindAt(0, ROAD_Y),
            "pavement cell stays outside the mask");
        boolean invertStructure = false;
        for (int y = mask.minY(); y < ROAD_Y; y++) {
            TunnelCrossSectionMask.CellKind kind = mask.kindAt(0, y);
            if (kind == TunnelCrossSectionMask.CellKind.AIR
                    || kind == TunnelCrossSectionMask.CellKind.LINING) {
                invertStructure = true;
                break;
            }
        }
        assertTrue(invertStructure, "circular must place invert air/lining below roadY");
    }

    @Test
    void ellipticalInvertExistsBelowRoadWithoutCarvingPavement() {
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            TunnelShape.ELLIPTICAL, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y, false);
        assertEquals(TunnelCrossSectionMask.CellKind.OUTSIDE, mask.kindAt(0, ROAD_Y));
        boolean invertStructure = false;
        for (int y = mask.minY(); y < ROAD_Y; y++) {
            TunnelCrossSectionMask.CellKind kind = mask.kindAt(1, y);
            if (kind == TunnelCrossSectionMask.CellKind.AIR
                    || kind == TunnelCrossSectionMask.CellKind.LINING) {
                invertStructure = true;
                break;
            }
        }
        assertTrue(invertStructure, "elliptical must place invert structure below roadY");
    }

    @Test
    void centerlineLiningThicknessAtCrownIsApproximatelyConfigured() {
        for (TunnelShape shape : TunnelShape.values()) {
            TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
                shape, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y, false);
            int half = (ENVELOPE + SIDE * 2) / 2;
            int innerTop = TunnelProfile.innerCeilingY(shape, 0, half, ROAD_Y, CLEAR);
            int liningCount = 0;
            for (int y = innerTop + 1; y <= mask.maxY(); y++) {
                if (mask.kindAt(0, y) == TunnelCrossSectionMask.CellKind.LINING) {
                    liningCount++;
                }
            }
            assertTrue(liningCount >= LINING,
                shape + " crown lining count=" + liningCount);
            assertTrue(liningCount <= LINING + 1,
                shape + " crown lining unexpectedly thick: " + liningCount);
        }
    }

    @Test
    void outerTopYTracksPerColumnShell() {
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            TunnelShape.PENTAGONAL, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y, false);
        int centerTop = mask.outerTopY(0);
        int edgeTop = mask.outerTopY((ENVELOPE + SIDE * 2) / 2);
        assertTrue(centerTop > edgeTop,
            "pentagonal peak must be higher on center than on edge walls");
    }
}
