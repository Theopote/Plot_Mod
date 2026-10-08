package com.plot.plugin.road.tunnel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelCrossSectionMaskTest {

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
}
