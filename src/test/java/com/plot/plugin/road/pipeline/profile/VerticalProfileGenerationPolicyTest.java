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
    void demotesInactiveManualProfileToFitTerrainAndDetachesDesignProfile() {
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
        assertTrue(road.getVerticalAlignment() == null || road.getVerticalAlignment().isEmpty());
    }

    @Test
    void detachesDormantDesignProfileFromFitTerrainRoads() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("terrain");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(40.0, 74.0))));
        addStraightEdge(network, road, 40.0);

        int demoted = VerticalProfileGenerationPolicy.applyTerrainFirstUnlessActive(network);

        assertEquals(0, demoted);
        assertTrue(road.getVerticalAlignment() == null || road.getVerticalAlignment().isEmpty());
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
    void clearsStaleJunctionManualElevationWhenManualProfileInactive() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("manual");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode junction = network.createNode(new Vec2d(40, 0));
        junction.setManualElevation(88.0);
        network.createEdge(
            start.getId(),
            junction.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)),
            road.getId());

        VerticalProfileGenerationPolicy.applyTerrainFirstUnlessActive(network);

        assertEquals(RoadVerticalMode.FIT_TERRAIN, road.getVerticalMode());
        assertTrue(junction.getManualElevation() == null);
    }

    @Test
    void preservesJunctionManualElevationForActiveManualProfile() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("confirmed");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode junction = network.createNode(new Vec2d(40, 0));
        junction.setManualElevation(88.0);
        network.createEdge(
            start.getId(),
            junction.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)),
            road.getId());

        VerticalProfileGenerationPolicy.applyTerrainFirstUnlessActive(network, Set.of(road.getId()));

        assertEquals(RoadVerticalMode.MANUAL_PROFILE, road.getVerticalMode());
        assertEquals(88.0, junction.getManualElevation());
    }

    @Test
    void preservesJunctionManualElevationForFlatRoad() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("flat");
        road.setVerticalMode(RoadVerticalMode.FLAT);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode junction = network.createNode(new Vec2d(40, 0));
        junction.setManualElevation(72.0);
        network.createEdge(
            start.getId(),
            junction.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)),
            road.getId());

        VerticalProfileGenerationPolicy.applyTerrainFirstUnlessActive(network);

        assertEquals(RoadVerticalMode.FLAT, road.getVerticalMode());
        assertEquals(72.0, junction.getManualElevation());
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
