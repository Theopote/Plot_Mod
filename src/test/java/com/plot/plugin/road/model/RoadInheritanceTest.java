package com.plot.plugin.road.model;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.RoadCrossSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadInheritanceTest {

    @Test
    void createRoadInheritsGlobalDefaultsDynamically() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(9);
        RoadNetwork network = new RoadNetwork();

        Road road = network.createRoad(config);

        assertNull(road.getWidth());
        assertEquals(9, road.getEffectiveWidth(config));

        config.setRoadWidth(11);
        assertEquals(11, road.getEffectiveWidth(config));
    }

    @Test
    void createRoadFromDefaultsSnapshotsExplicitValues() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(9);
        RoadNetwork network = new RoadNetwork();

        Road road = network.createRoadFromDefaults(config);

        assertEquals(9, road.getWidth());
        config.setRoadWidth(11);
        assertEquals(9, road.getEffectiveWidth(config));
    }

    @Test
    void inheritAllDefaultsClearsExplicitOverrides() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(7);
        config.setMaxSlope(10f);

        Road road = new Road();
        road.setWidth(9);
        road.setMaxSlope(5f);
        road.setIncludeShoulder(true);
        road.setStyleId("city_main");

        road.inheritAllDefaults();

        assertNull(road.getWidth());
        assertNull(road.getMaxSlope());
        assertNull(road.getStyleId());
        assertNull(road.getIncludeShoulder());
        assertEquals(7, road.getEffectiveWidth(config));
        assertTrue(road.getEffectiveIncludeShoulder(config));
    }

    @Test
    void inheritAllResetsCrossSectionComponents() {
        RoadCrossSection section = RoadCrossSection.fromConfig(new RoadSystemConfig("road_system"));
        section.getMedian().setEnabled(true);
        section.getBikeLane().setEnabled(true);
        section.getSlopeBatter().setEnabled(false);

        section.inheritAll();

        assertNull(section.getCarriageway().getWidth());
        assertNull(section.getMedian().getEnabled());
        assertNull(section.getBikeLane().getEnabled());
        assertNull(section.getSlopeBatter().getEnabled());
    }
}
