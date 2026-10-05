package com.plot.plugin.road;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.style.RoadStyle;
import com.plot.plugin.road.style.RoadStyleCatalog;
import com.plot.utils.PlotI18n;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCrossSectionPreviewRendererTest {

    private static final float PRESET_CARD_WIDTH = 120f;
    private static final float PRESET_CARD_HEIGHT = 48f;
    private static final RoadCrossSectionPreviewRenderer.MiniRenderOptions PRESET_OPTIONS =
        RoadCrossSectionPreviewRenderer.MiniRenderOptions.presetCard();

    @Test
    void layoutIncludesShoulderAndSidewalk() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(7);
        config.setIncludeSidewalk(true);
        config.setSidewalkWidth(2);
        config.setIncludeShoulder(true);
        config.setShoulderWidth(1);

        RoadCrossSectionPreviewRenderer.CrossSectionLayout layout =
            RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromConfig(config);

        assertEquals(7f, layout.roadBlocks);
        assertEquals(1f, layout.leftShoulderBlocks);
        assertEquals(2f, layout.leftSidewalkBlocks);
        assertEquals(13f, layout.totalWidthBlocks());
    }

    @Test
    void urbanPresetsDoNotShowSpuriousSlopeBatter() {
        for (RoadStyle style : new RoadStyle[] {
            RoadStyleCatalog.cityMain(),
            RoadStyleCatalog.residential(),
            RoadStyleCatalog.park()
        }) {
            var layout = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromStyle(style);
            assertFalse(layout.includeSlopeBatter, style.id);
        }
    }

    @Test
    void ruralPresetShowsShoulderAndSlopeBatter() {
        var layout = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromStyle(
            RoadStyleCatalog.countryRoad());
        assertTrue(layout.includeSlopeBatter);
        assertEquals(7f, layout.totalWidthBlocks());
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
    void presetCaptionDescribesCrossSectionOnly() {
        RoadStyle highway = RoadStyleCatalog.highway();
        var layout = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromStyle(highway);
        String caption = RoadCrossSectionPreviewRenderer.formatPresetCaption(highway, layout);
        assertEquals(
            PlotI18n.tr(
                "plugin.road.preset_cross_section_caption",
                highway.resolveLaneCount(),
                Math.round(layout.roadBlocks)),
            caption);
        assertFalse(caption.contains("纵坡"));
        assertFalse(caption.toLowerCase().contains("grade"));
    }

    @Test
    void presetCaptionUsesWidthOnlyForSingleLanePresets() {
        RoadStyle path = RoadStyleCatalog.path();
        var layout = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromStyle(path);
        String caption = RoadCrossSectionPreviewRenderer.formatPresetCaption(path, layout);
        assertEquals(
            PlotI18n.tr("plugin.road.preset_carriageway_width", Math.round(layout.roadBlocks)),
            caption);
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
        assertTrue(geometry.rightBatterPx() > 0f, "mountain card should show a cut triangle");
        float compactCap = Math.max(6f, PRESET_CARD_HEIGHT * 0.2f);
        assertTrue(geometry.rightBatterPx() <= compactCap + 0.01f,
            "compact cut batter should stay short: " + geometry.rightBatterPx());
        assertTrue(geometry.leftBatterPx() <= compactCap + 0.01f,
            "compact fill batter should stay short: " + geometry.leftBatterPx());
    }

    @Test
    void concreteVariantsResolveToDistinctPreviewColors() {
        int black = RoadCrossSectionPreviewRenderer.colorForMaterial("minecraft:black_concrete", 0);
        int gray = RoadCrossSectionPreviewRenderer.colorForMaterial("minecraft:gray_concrete", 0);
        int white = RoadCrossSectionPreviewRenderer.colorForMaterial("minecraft:white_concrete", 0);
        int cyan = RoadCrossSectionPreviewRenderer.colorForMaterial("minecraft:cyan_concrete", 0);
        int lightBlue = RoadCrossSectionPreviewRenderer.colorForMaterial("minecraft:light_blue_concrete", 0);
        int blue = RoadCrossSectionPreviewRenderer.colorForMaterial("minecraft:blue_concrete", 0);
        assertNotEquals(black, gray);
        assertNotEquals(gray, white);
        assertNotEquals(black, white);
        assertNotEquals(cyan, lightBlue);
        assertNotEquals(lightBlue, blue);
        assertNotEquals(cyan, black);
    }

    @Test
    void presetCardsKeepNativeMaterialsInsteadOfCurrentTheme() {
        var nativeStreet = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromStyle(
            RoadStyleCatalog.cityStreet());
        var medievalStreet = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromStyle(
            RoadStyleCatalog.cityStreet(), "medieval");
        assertNotEquals(nativeStreet.roadColor, medievalStreet.roadColor);

        var highway = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromStyle(
            RoadStyleCatalog.highway());
        var cityMain = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromStyle(
            RoadStyleCatalog.cityMain());
        var park = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromStyle(
            RoadStyleCatalog.park());
        var cyberpunk = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromStyle(
            RoadStyleCatalog.cyberpunkStreet());
        assertEquals(
            RoadCrossSectionPreviewRenderer.colorForMaterial("minecraft:black_concrete", 0),
            highway.roadColor);
        assertEquals(
            RoadCrossSectionPreviewRenderer.colorForMaterial("minecraft:white_concrete", 0),
            cityMain.roadColor);
        assertEquals(
            RoadCrossSectionPreviewRenderer.colorForMaterial("minecraft:gray_concrete", 0),
            nativeStreet.roadColor);
        assertEquals(
            RoadCrossSectionPreviewRenderer.colorForMaterial("minecraft:dirt_path", 0),
            park.roadColor);
        assertEquals(
            RoadCrossSectionPreviewRenderer.colorForMaterial("minecraft:light_blue_concrete", 0),
            park.bikeColor);
        assertEquals(
            RoadCrossSectionPreviewRenderer.colorForMaterial("minecraft:cyan_concrete", 0),
            cyberpunk.sidewalkColor);
        assertNotEquals(highway.roadColor, cityMain.roadColor);
        assertNotEquals(park.roadColor, cyberpunk.roadColor);
        assertNotEquals(park.bikeColor, cyberpunk.bikeColor);
    }

    @Test
    void presetCardGeometryFitsCatalogStylesWithinCardBounds() {
        for (RoadStyle style : RoadStyleCatalog.defaultStyles()) {
            assertPresetFitsCard(
                RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromStyle(style),
                style.id);
        }
    }

    @Test
    void grassAliasKeepsGreenPreviewColor() {
        int grass = RoadCrossSectionPreviewRenderer.colorForMaterial("material.plot.grass_block", 0);
        int stone = RoadCrossSectionPreviewRenderer.colorForMaterial("minecraft:stone", 0);
        assertNotEquals(stone, grass);
        assertEquals(
            RoadCrossSectionPreviewRenderer.colorForMaterial("minecraft:grass_block", 0),
            grass);
    }

    private static void assertPresetFitsCard(
            RoadCrossSectionPreviewRenderer.CrossSectionLayout layout) {
        assertPresetFitsCard(layout, "layout");
    }

    private static void assertPresetFitsCard(
            RoadCrossSectionPreviewRenderer.CrossSectionLayout layout,
            String styleId) {
        var geometry = RoadCrossSectionPreviewRenderer.previewGeometryForTests(
            layout, PRESET_CARD_WIDTH, PRESET_CARD_HEIGHT, PRESET_OPTIONS);
        assertNotNull(geometry, styleId);
        assertTrue(geometry.visualWidth() <= PRESET_CARD_WIDTH + 0.01f,
            styleId + " visual width should fit card: " + geometry.visualWidth());
        assertTrue(geometry.visualLeft() >= -0.01f,
            styleId + " left batter should not clip: " + geometry.visualLeft());
        assertTrue(geometry.visualRight() <= PRESET_CARD_WIDTH + 0.01f,
            styleId + " right batter should not clip: " + geometry.visualRight());
    }
}
