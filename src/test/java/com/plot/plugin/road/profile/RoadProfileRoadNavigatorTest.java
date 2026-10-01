package com.plot.plugin.road.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoadProfileRoadNavigatorTest {

    private List<Road> roads;

    @BeforeEach
    void setUp() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        roadA.setName("A");
        Road roadB = network.createRoad("road-b");
        roadB.setName("B");
        Road roadC = network.createRoad("road-c");
        roadC.setName("C");

        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), roadA.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), roadB.getId());
        network.createEdge(n3.getId(), n2.getId(), List.of(new Vec2d(20, 0), new Vec2d(10, 0)), roadC.getId());

        roads = RoadProfileRoadList.listStationableRoads(network);
    }

    @Test
    void editorRoadSelectionSwitchesBetweenRoads() {
        assertEquals("road-b", RoadProfileRoadNavigator.nextRoadId(roads, "road-a"));
        assertEquals("road-c", RoadProfileRoadNavigator.nextRoadId(roads, "road-b"));
        assertEquals("road-a", RoadProfileRoadNavigator.nextRoadId(roads, "road-c"));

        assertEquals("road-c", RoadProfileRoadNavigator.previousRoadId(roads, "road-a"));
        assertEquals("road-a", RoadProfileRoadNavigator.previousRoadId(roads, "road-b"));
        assertEquals("road-b", RoadProfileRoadNavigator.previousRoadId(roads, "road-c"));
    }

    @Test
    void normalizeRoadIdFallsBackWhenCurrentRoadRemoved() {
        assertEquals("road-a", RoadProfileRoadNavigator.normalizeRoadId(roads, "missing"));
        assertEquals("road-b", RoadProfileRoadNavigator.normalizeRoadId(roads, "road-b"));
    }

    @Test
    void switchingRoadClearsInvalidEditorRoadIdViaNormalize() {
        assertEquals("road-a", RoadProfileRoadNavigator.normalizeRoadId(roads, "deleted-road"));
        assertNull(RoadProfileRoadNavigator.normalizeRoadId(List.of(), "road-a"));
    }
}
