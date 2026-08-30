package com.plot.plugin.road;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.style.RoadStyleCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCrossSectionPreviewRendererTest {

    private static final float PRESET_CARD_WIDTH = 120f;
    private static final float PRESET_CARD_HEIGHT = 32f;
    private static final RoadCrossSectionPreviewRenderer.MiniRenderOptions PRESET_OPTIONS =
        RoadCrossSectionPreviewRenderer.MiniRenderOptions.presetCard();

    @Test
    void layoutIncludesShoulderSidewalkAndDrainage() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(7);
        config.setIncludeSidewalk(true);
        config.setSidewalkWidth(2);
        config.setIncludeShoulder(true);
        config.setShoulderWidth(1);
        config.setIncludeDrainage(true);

        RoadCrossSectionPreviewRenderer.CrossSectionLayout layout =
            RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromConfig(config);

        assertEquals(7f, layout.roadBlocks);
        assertEquals(1f, layout.leftShoulderBlocks);
        assertEquals(2f, layout.leftSidewalkBlocks);
        assertEquals(0.5f, layout.drainageBlocks);
        assertEquals(14f, layout.totalWidthBlocks());
    }

    @Test
    void presetCardGeometryFitsHighwayWithinCardBounds() {
        assertPresetFitsCard(RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromStyle(
            RoadStyleCatalog.highway()));
    }

    @Test
    void presetCardGeometryFitsMountainWithinCardBounds() {
        assertPresetFitsCard(RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromStyle(
            RoadStyleCatalog.mountain()));
    }

    @Test
    void presetCardGeometryFitsCountryRoadWithinCardBounds() {
        assertPresetFitsCard(RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromStyle(
            RoadStyleCatalog.countryRoad()));
    }

    @Test
    void presetCardCutSlopeStaysInsidePreviewArea() {
        var layout = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromStyle(
            RoadStyleCatalog.mountain());
        var geometry = RoadCrossSectionPreviewRenderer.previewGeometryForTests(
            layout, PRESET_CARD_WIDTH, PRESET_CARD_HEIGHT, PRESET_OPTIONS);
        assertNotNull(geometry);
        assertTrue(geometry.cutTopY() >= 1f, "cut slope should not extend above preview");
        assertTrue(geometry.topY() >= 1f, "preview top should stay inside card");
    }

    private static void assertPresetFitsCard(
            RoadCrossSectionPreviewRenderer.CrossSectionLayout layout) {
        var geometry = RoadCrossSectionPreviewRenderer.previewGeometryForTests(
            layout, PRESET_CARD_WIDTH, PRESET_CARD_HEIGHT, PRESET_OPTIONS);
        assertNotNull(geometry);
        assertTrue(geometry.visualWidth() <= PRESET_CARD_WIDTH + 0.01f,
            "visual width should fit card: " + geometry.visualWidth());
        assertTrue(geometry.visualLeft() >= -0.01f,
            "left batter should not clip: " + geometry.visualLeft());
        assertTrue(geometry.visualRight() <= PRESET_CARD_WIDTH + 0.01f,
            "right batter should not clip: " + geometry.visualRight());
    }
}
