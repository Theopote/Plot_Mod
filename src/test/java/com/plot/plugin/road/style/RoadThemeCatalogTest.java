package com.plot.plugin.road.style;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.RoadCrossSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RoadThemeCatalogTest {

    @Test
    void medievalThemeOverridesMaterialsOnCityStreet() {
        RoadStyle style = RoadStyleCatalog.cityStreet();
        RoadCrossSection modern = style.toCrossSection(RoadThemeCatalog.MODERN_ID);
        RoadCrossSection medieval = style.toCrossSection("medieval");

        assertEquals("minecraft:gray_concrete", modern.getCarriageway().getMaterial().getPrimaryMaterial());
        assertEquals("minecraft:cobblestone", medieval.getCarriageway().getMaterial().getPrimaryMaterial());
        assertEquals("minecraft:stone_bricks", medieval.getSidewalk().getMaterial());
    }

    @Test
    void modernThemePreservesPresetMaterials() {
        RoadStyle style = RoadStyleCatalog.residential();
        RoadCrossSection section = style.toCrossSection(RoadThemeCatalog.MODERN_ID);
        assertEquals("minecraft:gray_concrete", section.getCarriageway().getMaterial().getPrimaryMaterial());
    }

    @Test
    void applyThemeToConfigUpdatesCustomMaterials() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.markCustom();
        config.setRoadThemeId("desert");

        RoadThemeCatalog.applyThemeToConfig("desert", config);

        assertEquals("minecraft:smooth_sandstone", config.getSelectedMaterial().getPrimaryMaterial());
        assertEquals("minecraft:sandstone", config.getSelectedSidewalkMaterial());
    }

    @Test
    void configApplyStyleUsesSelectedTheme() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadThemeId("snow");
        config.applyStyle(RoadStyleCatalog.cityStreet());

        assertEquals("minecraft:packed_ice", config.getSelectedMaterial().getPrimaryMaterial());
        assertNotEquals("minecraft:gray_concrete", config.getSelectedMaterial().getPrimaryMaterial());
    }

    @Test
    void defaultThemesIncludeTenEntries() {
        assertEquals(10, RoadThemeCatalog.defaultThemes().size());
        assertNotNull(RoadThemeCatalog.findById("nether"));
    }
}
