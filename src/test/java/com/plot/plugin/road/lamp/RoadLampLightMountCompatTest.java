package com.plot.plugin.road.lamp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadLampLightMountCompatTest {
    @Test
    void groundFixturesRejectSuspendedMounts() {
        assertFalse(RoadLampStyle.Light.TORCH.supportsSuspended());
        assertFalse(RoadLampStyle.Light.CANDLE.supportsMount(RoadLampStyle.Mount.CHAIN));
        assertFalse(RoadLampStyle.Light.CAMPFIRE.supportsMount(RoadLampStyle.Mount.HOOD));
        assertTrue(RoadLampStyle.Light.TORCH.supportsMount(RoadLampStyle.Mount.TOP));
    }

    @Test
    void lanternsAndFullBlocksAllowSuspendedMounts() {
        assertTrue(RoadLampStyle.Light.LANTERN.supportsMount(RoadLampStyle.Mount.CHAIN));
        assertTrue(RoadLampStyle.Light.SEA.supportsMount(RoadLampStyle.Mount.HOOD));
        assertTrue(RoadLampStyle.Light.END_ROD.supportsMount(RoadLampStyle.Mount.CHAIN));
        assertTrue(RoadLampStyle.Light.END_ROD.block(true).endsWith("[facing=down]"));
        assertTrue(RoadLampStyle.Light.END_ROD.block(false).endsWith("[facing=up]"));
    }

    @Test
    void atypicalFixturesStayOutOfCatalogButLegacyLevelsCoerceUnsafeMounts() {
        assertFalse(RoadLampStyle.Light.RESPAWN_ANCHOR.inCatalog());
        assertFalse(RoadLampStyle.Light.FIREFLY_BUSH.inCatalog());
        var coerced = new RoadLampStyle.Level(
            8, RoadLampStyle.Layout.TWIN, RoadLampStyle.Mount.CHAIN, 2, 1, RoadLampStyle.Light.TORCH);
        assertEquals(RoadLampStyle.Light.LANTERN, coerced.light());
    }
}
