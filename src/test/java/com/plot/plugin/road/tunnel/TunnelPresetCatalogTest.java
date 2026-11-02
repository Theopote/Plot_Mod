package com.plot.plugin.road.tunnel;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TunnelPresetCatalogTest {

    private static final Set<String> EMITTING_LIGHTS = Set.of(
        "minecraft:glowstone",
        "minecraft:sea_lantern",
        "minecraft:shroomlight");

    @Test
    void everyPresetWritesDistinctDefaultShapeCoverage() {
        EnumSet<TunnelShape> covered = EnumSet.noneOf(TunnelShape.class);
        for (TunnelPreset preset : TunnelPreset.values()) {
            TunnelStyle style = new TunnelStyle();
            TunnelPresetCatalog.apply(preset, style);
            assertNotNull(style.getShape(), preset.name());
            covered.add(style.getShape());
            assertTrue(EMITTING_LIGHTS.contains(style.getLightMaterial()),
                preset + " light must emit without attachment/power: " + style.getLightMaterial());
        }
        assertEquals(7, covered.size(), "presets should cover all seven shapes, got " + covered);
    }

    @Test
    void modernHighwayUsesDShape() {
        TunnelStyle style = new TunnelStyle();
        TunnelPresetCatalog.apply(TunnelPreset.MODERN_HIGHWAY, style);
        assertEquals(TunnelShape.D_SHAPE, style.getShape());
        assertEquals(TunnelLightingMode.WALL_AND_CEILING, style.getLightingMode());
    }

    @Test
    void metroUsesCircularWithoutClaimingRails() {
        TunnelStyle style = new TunnelStyle();
        TunnelPresetCatalog.apply(TunnelPreset.METRO, style);
        assertEquals(TunnelShape.CIRCULAR, style.getShape());
        assertFalse(style.getLiningMaterial().contains("rail"));
    }

    @Test
    void arcticUsesIceLiningButEmittingLight() {
        TunnelStyle style = new TunnelStyle();
        TunnelPresetCatalog.apply(TunnelPreset.ARCTIC_SNOW, style);
        assertEquals(TunnelShape.ELLIPTICAL, style.getShape());
        assertEquals("minecraft:packed_ice", style.getLiningMaterial());
        assertEquals("minecraft:sea_lantern", style.getLightMaterial());
    }

    @Test
    void matchesRequiresFullFieldEqualityNotJustShape() {
        TunnelStyle luxury = new TunnelStyle();
        TunnelPresetCatalog.apply(TunnelPreset.LUXURY_SCENIC, luxury);
        assertTrue(TunnelPresetCatalog.matches(TunnelPreset.LUXURY_SCENIC, luxury));
        assertFalse(TunnelPresetCatalog.matches(TunnelPreset.ARCTIC_SNOW, luxury),
            "same ELLIPTICAL shape must not match a different preset");
        luxury.setClearHeight(luxury.getClearHeight() + 1);
        assertFalse(TunnelPresetCatalog.matches(TunnelPreset.LUXURY_SCENIC, luxury));
    }
}
