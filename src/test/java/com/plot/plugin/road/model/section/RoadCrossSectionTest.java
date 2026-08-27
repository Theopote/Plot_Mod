package com.plot.plugin.road.model.section;

import com.plot.plugin.config.RoadSystemConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCrossSectionTest {

    @Test
    void applyDefaultsCopiesAllCrossSectionComponents() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(7);
        config.setIncludeShoulder(false);
        config.setFillSlopeRatio(2.5f);
        config.setCutSlopeRatio(1.2f);
        config.setIncludeSidewalk(true);
        config.setLaneDividers(true);

        RoadCrossSection section = new RoadCrossSection();
        section.getMedian().setEnabled(true);
        section.getMedian().setWidth(2);
        section.getBikeLane().setEnabled(true);
        section.getBikeLane().setWidth(2);
        section.getMarkings().setCenterLineStyle(CenterLineStyle.DOUBLE_SOLID);
        section.getStreetFurniture().setStreetlightSpacing(12);
        section.getSlopeBatter().setEnabled(false);

        section.applyDefaults(config);

        assertEquals(7, section.getCarriageway().getWidth());
        assertFalse(section.getShoulder().getEnabled());
        assertTrue(section.getSidewalk().getEnabled());
        assertFalse(section.getDrain().getEnabled());
        assertFalse(section.getMedian().getEnabled());
        assertFalse(section.getBikeLane().getEnabled());
        assertNull(section.getStreetFurniture().getStreetlightSpacing());
        assertEquals(2.5f, section.getSlopeBatter().getFillRatio());
        assertTrue(section.getSlopeBatter().getEnabled());
        assertTrue(section.getMarkings().getLaneDividers());
        assertFalse(section.getMarkings().getCenterLine());
    }

    @Test
    void fieldResolutionTracksExplicitOverride() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(5);

        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(9);

        CrossSectionFieldResolution<Integer> width = CrossSectionFieldResolution.of(
            section.getCarriageway().getWidth(),
            config.getRoadWidth(),
            section.resolve(config).carriagewayWidth);

        assertFalse(width.inherited());
        assertEquals(9, width.explicitOverride());
        assertEquals(9, width.resolved());
    }

    @Test
    void fieldResolutionTracksInheritedDefault() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(5);

        RoadCrossSection section = new RoadCrossSection();

        CrossSectionFieldResolution<Integer> width = CrossSectionFieldResolution.of(
            section.getCarriageway().getWidth(),
            config.getRoadWidth(),
            section.resolve(config).carriagewayWidth);

        assertTrue(width.inherited());
        assertNull(width.explicitOverride());
        assertEquals(5, width.resolved());
        assertEquals(5, width.inheritedDefault());
    }
}
