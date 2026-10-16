package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalProfileGenerationPolicyTest {

    @Test
    void demotesInactiveManualProfileToFitTerrainKeepingAlignment() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("manual");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(40.0, 74.0))));
        addStraightEdge(network, road, 40.0);

        int demoted = VerticalProfileGenerationPolicy.applyTerrainFirstUnlessActive(network);

        assertEquals(1, demoted);
        assertEquals(RoadVerticalMode.FIT_TERRAIN, road.getVerticalMode());
        assertNotNull(road.getVerticalAlignment());
        assertEquals(2, road.getVerticalAlignment().pviCount());
    }

    @Test
    void keepsActiveManualProfileForGeneration() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("confirmed");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(40.0, 74.0))));
        addStraightEdge(network, road, 40.0);

        int demoted = VerticalProfileGenerationPolicy.applyTerrainFirstUnlessActive(
            network, Set.of(road.getId()));

        assertEquals(0, demoted);
        assertEquals(RoadVerticalMode.MANUAL_PROFILE, road.getVerticalMode());
        assertTrue(VerticalAlignmentProfileSupport.shouldUseVerticalAlignment(network, road));
    }

    @Test
    void leavesFitTerrainUnchanged() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("terrain");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        addStraightEdge(network, road, 40.0);

        int demoted = VerticalProfileGenerationPolicy.applyTerrainFirstUnlessActive(network);

        assertEquals(0, demoted);
        assertEquals(RoadVerticalMode.FIT_TERRAIN, road.getVerticalMode());
    }

    private static void addStraightEdge(RoadNetwork network, Road road, double length) {
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(length, 0));
        network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(length, 0)),
            road.getId());
    }
}
