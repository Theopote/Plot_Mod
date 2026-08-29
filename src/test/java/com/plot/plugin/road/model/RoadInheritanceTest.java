package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.model.section.RoadCrossSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadInheritanceTest {

    private final RoadNetworkBuilder builder = new RoadNetworkBuilder();
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
        assertFalse(RoadParameterInheritance.inheritsWidth(road));
    }

    @Test
    void adoptShapeSnapshotsDefaultsAtClaimTime() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(9);
        config.setMaxSlope(8f);
        RoadNetwork network = new RoadNetwork();

        builder.adoptShape(network, new PolylineShape(
            java.util.List.of(new Vec2d(0, 0), new Vec2d(10, 0)), false), config);

        Road road = network.getRoads().values().iterator().next();
        assertEquals(9, road.getWidth());
        assertEquals(8f, road.getMaxSlope());

        config.setRoadWidth(11);
        config.setMaxSlope(12f);
        assertEquals(9, road.getEffectiveWidth(config));
        assertEquals(8f, road.getEffectiveMaxSlope(config));
        assertFalse(RoadParameterInheritance.inheritsMaxSlope(road));
    }

    @Test
    void snapshotGlobalDefaultsAppliesSelectedStyle() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.applyStyle(config.getStyles().getFirst());
        Road road = new Road();

        RoadParameterInheritance.snapshotGlobalDefaults(road, config);

        assertEquals(config.getStyles().getFirst().id, road.getStyleId());
        assertEquals(config.getStyles().getFirst().width, road.getWidth());
    }

    @Test
    void resolveWidthTracksInheritanceLayers() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(7);
        Road road = new Road();

        var inherited = RoadParameterInheritance.resolveWidth(road, config);
        assertTrue(inherited.inherited());
        assertEquals(7, inherited.resolved());

        road.setWidth(9);
        var overridden = RoadParameterInheritance.resolveWidth(road, config);
        assertFalse(overridden.inherited());
        assertEquals(9, overridden.resolved());
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
