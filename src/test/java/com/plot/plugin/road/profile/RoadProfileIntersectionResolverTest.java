package com.plot.plugin.road.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadProfileIntersectionResolverTest {

    @Test
    void resolvesCrossingAtRoadEndpoint() {
        RoadNetwork network = new RoadNetwork();
        RoadSystemConfig config = new RoadSystemConfig("test");
        Road roadA = network.createRoad("Main");
        Road roadB = network.createRoad("Side");
        roadA.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 72),
            PointOfVerticalIntersection.of(100, 72))));
        roadB.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 68),
            PointOfVerticalIntersection.of(50, 68))));

        var west = network.createNode(new Vec2d(0, 0));
        var center = network.createNode(new Vec2d(100, 0));
        var north = network.createNode(new Vec2d(100, 50));
        RoadEdge mainWest = network.createEdge(
            west.getId(), center.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)), roadA.getId());
        network.createEdge(
            center.getId(), north.getId(),
            List.of(new Vec2d(100, 0), new Vec2d(100, 50)), roadB.getId());

        RoadGenerationResult profile = new RoadGenerationResult(100);
        profile.profileDistances = List.of(0.0, 50.0, 100.0);
        profile.profileGroundHeights = List.of(70, 70, 70);
        profile.profileGuideLine = List.of(71, 71, 71);
        profile.profileTargetHeights = List.of(72, 72, 72);

        List<RoadProfileIntersection> intersections = RoadProfileIntersectionResolver.forEdge(
            network, roadA, mainWest, config, profile);

        assertEquals(1, intersections.size());
        RoadProfileIntersection crossing = intersections.getFirst();
        assertEquals(roadB.getId(), crossing.otherRoadId());
        assertEquals(100.0, crossing.localDistance(), 1e-6);
        assertEquals(72.0, crossing.currentRoadElevation(), 1e-6);
        assertFalse(crossing.gradeSeparated());
    }

    @Test
    void resolvesGradeSeparatedCrossing() {
        RoadNetwork network = new RoadNetwork();
        RoadSystemConfig config = new RoadSystemConfig("test");
        Road roadA = network.createRoad("Highway");
        Road roadB = network.createRoad("Local");
        roadA.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 76),
            PointOfVerticalIntersection.of(100, 76))));
        roadB.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 68),
            PointOfVerticalIntersection.of(50, 68))));

        var west = network.createNode(new Vec2d(0, 0));
        var center = network.createNode(new Vec2d(50, 0));
        var east = network.createNode(new Vec2d(100, 0));
        var north = network.createNode(new Vec2d(50, 50));
        var south = network.createNode(new Vec2d(50, -50));
        RoadEdge mainWest = network.createEdge(
            west.getId(), center.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(50, 0)), roadA.getId());
        network.createEdge(
            center.getId(), east.getId(),
            List.of(new Vec2d(50, 0), new Vec2d(100, 0)), roadA.getId());
        network.createEdge(
            center.getId(), north.getId(),
            List.of(new Vec2d(50, 0), new Vec2d(50, 50)), roadB.getId());
        network.createEdge(
            center.getId(), south.getId(),
            List.of(new Vec2d(50, 0), new Vec2d(50, -50)), roadB.getId());
        assertTrue(network.setNodeGradeSeparation(center.getId(), true, roadA.getId(), 4.0));

        RoadGenerationResult profile = new RoadGenerationResult(50);
        profile.profileDistances = List.of(0.0, 50.0);
        profile.profileGroundHeights = List.of(70, 70);
        profile.profileGuideLine = List.of(71, 71);
        profile.profileTargetHeights = List.of(76, 76);

        List<RoadProfileIntersection> intersections = RoadProfileIntersectionResolver.forEdge(
            network, roadA, mainWest, config, profile);

        assertEquals(1, intersections.size());
        RoadProfileIntersection crossing = intersections.getFirst();
        assertTrue(crossing.gradeSeparated());
        assertTrue(crossing.currentRoadElevated());
        assertEquals(4.0, crossing.clearance(), 1e-6);
    }
}
