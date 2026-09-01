package com.plot.plugin.road.style;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadStyleCatalogSlopeTest {

    @Test
    void builtinStylesUseRoadSpecificMaximumSlopes() {
        assertEquals(5.0f, RoadStyleCatalog.avenue().maxSlope);
        assertEquals(6.0f, RoadStyleCatalog.cityStreet().maxSlope);
        assertEquals(6.0f, RoadStyleCatalog.cityMain().maxSlope);
        assertEquals(8.0f, RoadStyleCatalog.residential().maxSlope);
        assertEquals(10.0f, RoadStyleCatalog.villageRoad().maxSlope);
        assertEquals(12.0f, RoadStyleCatalog.mountain().maxSlope);
        assertEquals(16.0f, RoadStyleCatalog.path().maxSlope);
    }

    @Test
    void applyingStyleSetsRoadSpecificMaximumSlope() {
        Road road = new Road("mountain-road");
        RoadStyleCatalog.mountain().applyTo(road);

        assertEquals(12.0f, road.getEffectiveMaxSlope(new RoadSystemConfig("road_system")));
    }
}
