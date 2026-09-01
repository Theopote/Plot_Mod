package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadVerticalModeTest {
    @Test void legacyRoadInfersModeFromAlignmentPresence() {
        Road road = new Road("legacy");
        assertEquals(RoadVerticalMode.AUTO_SMOOTH, road.getVerticalMode());
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(20, 72))));
        assertEquals(RoadVerticalMode.MANUAL_PROFILE, road.getVerticalMode());
    }

    @Test void explicitAutomaticModeCanRetainDormantManualProfile() {
        Road road = new Road("road");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(20, 72))));
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        assertEquals(RoadVerticalMode.FIT_TERRAIN, road.getVerticalMode());
    }
}
