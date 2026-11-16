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
        config.setLaneCount(2);
        config.setDefaultLaneWidth(3);
        config.setCenterLineStyle(CenterLineStyle.SINGLE_DASHED);
        config.setLaneDividers(true);
        config.setIncludeShoulder(false);
        config.setFillSlopeRatio(2.5f);
        config.setCutSlopeRatio(1.2f);
        config.setIncludeSidewalk(true);

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
        assertEquals(3, section.getCarriageway().getEffectiveDefaultLaneWidth());
        assertFalse(section.getShoulder().getEnabled());
        assertTrue(section.getSidewalk().getEnabled());
        assertFalse(section.getMedian().getEnabled());
        assertFalse(section.getBikeLane().getEnabled());
        assertNull(section.getStreetFurniture().getStreetlightSpacing());
        assertEquals(2.5f, section.getSlopeBatter().getFillRatio());
        assertTrue(section.getSlopeBatter().getEnabled());
        assertTrue(section.getMarkings().getLaneDividers());
        assertEquals(CenterLineStyle.SINGLE_DASHED, section.getMarkings().getCenterLineStyle());
        assertTrue(section.getMarkings().getCenterLine());
    }

    @Test
    void fieldResolutionTracksExplicitOverride() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setDefaultLaneWidth(3);

        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setDefaultLaneWidth(6);
        section.getCarriageway().syncDerivedWidth(CenterLineStyle.NONE, false, 0);

        CrossSectionFieldResolution<Integer> width = CrossSectionFieldResolution.of(
            section.getCarriageway().getWidth(),
            config.getRoadWidth(),
            section.resolve(config).carriagewayWidth);

        assertFalse(width.inherited());
        assertEquals(6, width.explicitOverride());
        assertEquals(6, width.resolved());
    }

    @Test
    void fieldResolutionTracksInheritedDefault() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setDefaultLaneWidth(5);

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
