package com.plot.plugin.road.style;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Built-in presets should not place gravity-affected blocks (sand/gravel) on shoulders or slopes.
 */
class RoadPresetGravitySafeMaterialsTest {

    private static final Set<String> GRAVITY_BLOCKS = Set.of(
        "minecraft:gravel",
        "minecraft:sand",
        "minecraft:red_sand",
        "minecraft:suspicious_sand",
        "minecraft:suspicious_gravel");

    @Test
    void builtinStylesAvoidGravityBlocksOnShoulderAndSlopes() {
        for (RoadStyle style : RoadStyleCatalog.defaultStyles()) {
            assertNotGravity(style.shoulderMaterial, style.id + " shoulder");
            assertNotGravity(style.fillSlopeMaterial, style.id + " fillSlope");
            assertNotGravity(style.cutSlopeMaterial, style.id + " cutSlope");
        }
    }

    @Test
    void builtinThemesAvoidGravityBlocksOnShoulderAndSlopes() {
        for (RoadTheme theme : RoadThemeCatalog.defaultThemes()) {
            assertNotGravity(theme.shoulderMaterial, theme.id + " shoulder");
            assertNotGravity(theme.fillSlopeMaterial, theme.id + " fillSlope");
            assertNotGravity(theme.cutSlopeMaterial, theme.id + " cutSlope");
        }
    }

    private static void assertNotGravity(String material, String label) {
        if (material == null || material.isBlank()) {
            return;
        }
        String normalized = com.plot.plugin.road.RoadMaterialUtils.normalizeStoredMaterial(material);
        String blockId = normalized != null ? normalized : material;
        assertFalse(GRAVITY_BLOCKS.contains(blockId), label + " must not use " + blockId);
    }
}
