package com.plot.plugin.road.vertical;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlatVerticalIntentSupportRecommendTest {

    @Test
    void recommendBaseElevationUsesSampledProfileMedianNotPviCountAverage() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoads().values().iterator().next();
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 60),
            PointOfVerticalIntersection.of(10, 100),
            PointOfVerticalIntersection.of(100, 60))));
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);

        double pviAverage = FlatVerticalIntentSupport.pviCountAverageElevation(road.getVerticalAlignment());
        double recommended = FlatVerticalIntentSupport.recommendBaseElevation(network, road);

        assertEquals(220.0 / 3.0, pviAverage, 1e-6);
        assertNotEquals(pviAverage, recommended, 0.05);
        assertEquals(80.0, recommended, 0.01);
    }

    @Test
    void recommendBaseElevationReturnsStoredFlatIntentBase() {
        RoadNetwork network = straightRoad(80.0);
        Road road = network.getRoads().values().iterator().next();
        FlatVerticalIntentSupport.enableFlatWithBase(
            network, road, new RoadSystemConfig("test"), 71.5);

        assertEquals(71.5, FlatVerticalIntentSupport.recommendBaseElevation(network, road), 1e-6);
    }

    private static RoadNetwork straightRoad(double length) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(length, 0));
        network.createEdge(start.getId(), end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(length, 0)), road.getId());
        return network;
    }
}
