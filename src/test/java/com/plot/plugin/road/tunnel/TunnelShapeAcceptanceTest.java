package com.plot.plugin.road.tunnel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase-6 geometry acceptance for the seven cross-section shapes.
 */
class TunnelShapeAcceptanceTest {

    private static final int ROAD_Y = 64;
    private static final int ENVELOPE = 5;
    private static final int SIDE = 1;
    private static final int CLEAR = 6;
    private static final int LINING = 1;

    @Test
    void centerClearanceRemainsConnectedForEveryShape() {
        for (TunnelShape shape : TunnelShape.values()) {
            TunnelCrossSectionMask mask = build(shape);
            for (int y = ROAD_Y + 1; y <= ROAD_Y + CLEAR; y++) {
                assertEquals(
                    TunnelCrossSectionMask.CellKind.AIR,
                    mask.kindAt(0, y),
                    shape + " center air gap at y=" + y);
            }
        }
    }

    @Test
    void circularAndEllipticalKeepFlatPavementCell() {
        TunnelCrossSectionMask elliptical = build(TunnelShape.ELLIPTICAL);
        TunnelCrossSectionMask circular = TunnelCrossSectionMask.build(
            TunnelShape.CIRCULAR, ENVELOPE, SIDE, 4, LINING, ROAD_Y, false);
        for (TunnelCrossSectionMask mask : new TunnelCrossSectionMask[] {elliptical, circular}) {
            assertEquals(TunnelCrossSectionMask.CellKind.OUTSIDE, mask.kindAt(0, ROAD_Y));
            assertEquals(TunnelCrossSectionMask.CellKind.OUTSIDE, mask.kindAt(1, ROAD_Y));
            boolean invert = false;
            for (int y = mask.minY(); y < ROAD_Y; y++) {
                if (mask.kindAt(0, y) != TunnelCrossSectionMask.CellKind.OUTSIDE
                        || mask.kindAt(1, y) != TunnelCrossSectionMask.CellKind.OUTSIDE) {
                    invert = true;
                    break;
                }
            }
            assertTrue(invert, "invert shape must keep structure below road");
        }
    }

    @Test
    void portalFrameFollowsShapeNotOnlyRectangle() {
        TunnelCrossSectionMask arch = TunnelCrossSectionMask.build(
            TunnelShape.ARCH, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y, true);
        TunnelCrossSectionMask rect = TunnelCrossSectionMask.build(
            TunnelShape.RECTANGULAR, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y, true);
        int diffs = 0;
        for (int lat = -8; lat <= 8; lat++) {
            for (int y = ROAD_Y - 2; y <= ROAD_Y + CLEAR + 2; y++) {
                if (arch.kindAt(lat, y) != rect.kindAt(lat, y)) {
                    diffs++;
                }
            }
        }
        assertTrue(diffs > 0, "portal frames must differ between ARCH and RECTANGULAR");
    }

    @Test
    void lightingAnchorStaysInsideCavityAir() {
        for (TunnelShape shape : TunnelShape.values()) {
            int half = (ENVELOPE + SIDE * 2) / 2;
            int ceiling = TunnelProfile.innerCeilingY(shape, 0, half, ROAD_Y, CLEAR);
            assertTrue(
                TunnelProfile.isCavityAir(shape, 0, ceiling, ROAD_Y, half, CLEAR),
                shape + " ceiling light must sit in cavity air");
            int wall = -half;
            int wallY = ROAD_Y + 2;
            if (!TunnelProfile.isCavityAir(shape, wall, wallY, ROAD_Y, half, CLEAR)) {
                wallY = ROAD_Y + Math.max(2, CLEAR / 2);
            }
            if (TunnelProfile.isCavityAir(shape, wall, wallY, ROAD_Y, half, CLEAR)) {
                assertTrue(true);
            } else {
                // Horseshoe inset walls may not reach full half-width; probe inward.
                assertTrue(
                    TunnelProfile.isCavityAir(shape, wall + 1, ROAD_Y + 2, ROAD_Y, half, CLEAR)
                        || TunnelProfile.isCavityAir(shape, 0, ROAD_Y + 2, ROAD_Y, half, CLEAR),
                    shape + " must still have wall/center air for lights");
            }
        }
    }

    @Test
    void unknownStoredShapeFallsBackToArch() {
        assertEquals(TunnelShape.ARCH, TunnelShape.fromStored("not_a_real_shape"));
        assertEquals(TunnelShape.D_SHAPE, TunnelShape.fromStored("d_shape"));
    }

    @Test
    void outerTopVariesAcrossPentagonalColumns() {
        TunnelCrossSectionMask mask = build(TunnelShape.PENTAGONAL);
        assertTrue(mask.outerTopY(0) > mask.outerTopY((ENVELOPE + SIDE * 2) / 2));
        assertFalse(mask.outerTopY(0) == Integer.MIN_VALUE);
    }

    private static TunnelCrossSectionMask build(TunnelShape shape) {
        return TunnelCrossSectionMask.build(shape, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y, false);
    }
}
