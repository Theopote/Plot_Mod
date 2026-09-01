package com.plot.plugin.road.style;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.RoadCrossSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadStyleTest {

    @Test
    void mountainStyleSetsSlopeAndMaxGrade() {
        RoadStyle style = RoadStyleCatalog.mountain();
        RoadCrossSection section = style.toCrossSection();

        assertTrue(section.getShoulder().getEnabled());
        assertTrue(section.getSlopeBatter().getEnabled());
        assertEquals(2.5f, section.getSlopeBatter().getFillRatio(), 1e-3);
        assertEquals(12.0f, style.maxSlope, 1e-3);
    }

    @Test
    void parkStyleIncludesBikeLaneWithoutSlopeBatter() {
        RoadStyle style = RoadStyleCatalog.park();
        RoadCrossSection section = style.toCrossSection();

        assertTrue(section.getBikeLane().getEnabled());
        assertEquals(1, section.getBikeLane().getWidth());
        assertFalse(Boolean.TRUE.equals(section.getSlopeBatter().getEnabled()));
    }

    @Test
    void highwayStyleSetsMedianAndDoubleCenterLine() {
        RoadStyle style = RoadStyleCatalog.highway();
        RoadCrossSection section = style.toCrossSection();

        assertTrue(section.getMedian().getEnabled());
        assertEquals(CenterLineStyle.DOUBLE_SOLID, section.getMarkings().getCenterLineStyle());
    }

    @Test
    void applyToRoadSetsStyleIdAndCrossSection() {
        Road road = new Road();
        RoadStyle style = RoadStyleCatalog.residential();

        style.applyTo(road);

        assertEquals("residential", road.getStyleId());
        assertEquals(style.width, road.getWidth());
        assertEquals(style.maxSlope, road.getMaxSlope());
        assertTrue(road.getIncludeSidewalk());
    }

    @Test
    void applyStyleByIdResolvesFromCatalog() {
        Road road = new Road();
        road.applyStyle("mountain", new RoadSystemConfig("road_system"));

        assertEquals("mountain", road.getStyleId());
        assertNotNull(road.getCrossSection().getSlopeBatter().getFillRatio());
    }

    @Test
    void citySecondaryAliasStillResolvable() {
        RoadStyle style = RoadStyleCatalog.findById(null, "city_secondary");
        assertNotNull(style);
        assertEquals("city_secondary", style.id);
    }

    @Test
    void configDefaultStylesIncludeNewEntries() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        assertTrue(config.getStyles().stream().anyMatch(s -> "mountain".equals(s.id)));
        assertTrue(config.getStyles().stream().anyMatch(s -> "residential".equals(s.id)));
        assertTrue(config.getStyles().stream().anyMatch(s -> "industrial".equals(s.id)));
        assertTrue(config.getStyles().stream().anyMatch(s -> "path".equals(s.id)));
        assertTrue(config.getStyles().stream().anyMatch(s -> "city_street".equals(s.id)));
        assertTrue(config.getStyles().stream().anyMatch(s -> "boulevard".equals(s.id)));
    }

    @Test
    void avenuePresetHasMedian() {
        RoadCrossSection section = RoadStyleCatalog.avenue().toCrossSection();
        assertTrue(section.getMedian().getEnabled());
        assertEquals(1, section.getMedian().getWidth());
    }

    @Test
    void pathPresetIsMinimalWidth() {
        RoadStyle style = RoadStyleCatalog.path();
        assertEquals(3, style.width);
        assertFalse(style.hasSidewalk);
    }
}
