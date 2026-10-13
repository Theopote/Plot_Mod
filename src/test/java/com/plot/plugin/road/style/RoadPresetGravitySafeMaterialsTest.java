package com.plot.plugin.road.style;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.construction.RoadConstructionMaterialPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Built-in presets and themes must resolve to gravity-safe materials across all roles.
 */
class RoadPresetGravitySafeMaterialsTest {

    @Test
    void builtinStylesAvoidGravityBlocksOnAllRoles() {
        RoadSystemConfig defaults = new RoadSystemConfig("gravity-safe");
        for (RoadStyle style : RoadStyleCatalog.defaultStyles()) {
            ResolvedCrossSection resolved = ResolvedCrossSection.resolve(style.toCrossSection(), defaults);
            assertSafe(resolved.carriagewayMaterial.getPrimaryMaterial(), style.id + " carriageway");
            assertSafe(resolved.shoulderMaterial, style.id + " shoulder");
            assertSafe(resolved.sidewalkMaterial, style.id + " sidewalk");
            assertSafe(resolved.bikeLaneMaterial, style.id + " bike");
            assertSafe(resolved.medianMaterial, style.id + " median");
            assertSafe(resolved.markingMaterial, style.id + " marking");
            assertSafe(resolved.fillSlopeMaterial, style.id + " fillSlope");
            assertSafe(resolved.cutSlopeMaterial, style.id + " cutSlope");
            assertSafe(style.roadMaterial, style.id + " roadMaterial raw");
        }
    }

    @Test
    void builtinThemesAvoidGravityBlocksOnAllRoles() {
        for (RoadTheme theme : RoadThemeCatalog.defaultThemes()) {
            assertSafe(theme.roadMaterial, theme.id + " road");
            assertSafe(theme.sidewalkMaterial, theme.id + " sidewalk");
            assertSafe(theme.shoulderMaterial, theme.id + " shoulder");
            assertSafe(theme.markingMaterial, theme.id + " marking");
            assertSafe(theme.fillSlopeMaterial, theme.id + " fillSlope");
            assertSafe(theme.cutSlopeMaterial, theme.id + " cutSlope");
        }
    }

    private static void assertSafe(String material, String label) {
        if (material == null || material.isBlank()) {
            return;
        }
        String normalized = com.plot.plugin.road.RoadMaterialUtils.normalizeStoredMaterial(material);
        String blockId = normalized != null ? normalized : material;
        assertFalse(
            RoadConstructionMaterialPolicy.isGravityUnsafe(blockId),
            label + " must not use " + blockId);
    }
}
