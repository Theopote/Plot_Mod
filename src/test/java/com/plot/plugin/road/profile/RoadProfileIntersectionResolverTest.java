package com.plot.plugin.road.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadProfileIntersectionResolverTest {

    @Test
    void forRoadUsesCanonicalRoadStation() {
        RoadNetwork network = new RoadNetwork();
        RoadSystemConfig config = new RoadSystemConfig("test");
        Road roadA = network.createRoad("Main");
        Road roadB = network.createRoad("Side");
        markEdited(roadA, List.of(
            PointOfVerticalIntersection.of(0, 72),
            PointOfVerticalIntersection.of(100, 72)));
        markEdited(roadB, List.of(
            PointOfVerticalIntersection.of(0, 68),
            PointOfVerticalIntersection.of(50, 68)));

        var west = network.createNode(new Vec2d(0, 0));
        var center = network.createNode(new Vec2d(100, 0));
        var north = network.createNode(new Vec2d(100, 50));
        RoadEdge mainWest = network.createEdge(
            west.getId(), center.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)), roadA.getId());
        network.createEdge(
            center.getId(), north.getId(),
            List.of(new Vec2d(100, 0), new Vec2d(100, 50)), roadB.getId());

        RoadGenerationResult profile = profileSeries(
            List.of(0.0, 50.0, 100.0),
            List.of(70, 70, 70),
            List.of(71, 71, 71),
            List.of(72, 72, 72));

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        edgeResults.put(mainWest.getId(), profile);

        List<RoadProfileIntersection> intersections = RoadProfileIntersectionResolver.forRoad(
            network, roadA, config, edgeResults);

        assertEquals(1, intersections.size());
        assertEquals(100.0, intersections.getFirst().roadStation(), 1e-6);
        assertEquals(roadB.getId(), intersections.getFirst().otherRoadId());
    }

    @Test
    void resolvesCrossingAtRoadEndpoint() {
        RoadNetwork network = new RoadNetwork();
        RoadSystemConfig config = new RoadSystemConfig("test");
        Road roadA = network.createRoad("Main");
        Road roadB = network.createRoad("Side");
        markEdited(roadA, List.of(
            PointOfVerticalIntersection.of(0, 72),
            PointOfVerticalIntersection.of(100, 72)));
        markEdited(roadB, List.of(
            PointOfVerticalIntersection.of(0, 68),
            PointOfVerticalIntersection.of(50, 68)));

        var west = network.createNode(new Vec2d(0, 0));
        var center = network.createNode(new Vec2d(100, 0));
        var north = network.createNode(new Vec2d(100, 50));
        RoadEdge mainWest = network.createEdge(
            west.getId(), center.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)), roadA.getId());
        network.createEdge(
            center.getId(), north.getId(),
            List.of(new Vec2d(100, 0), new Vec2d(100, 50)), roadB.getId());

        RoadGenerationResult profile = profileSeries(
            List.of(0.0, 50.0, 100.0),
            List.of(70, 70, 70),
            List.of(71, 71, 71),
            List.of(72, 72, 72));

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        edgeResults.put(mainWest.getId(), profile);

        List<RoadProfileIntersection> intersections = RoadProfileIntersectionResolver.forRoad(
            network, roadA, config, edgeResults);

        assertEquals(1, intersections.size());
        RoadProfileIntersection crossing = intersections.getFirst();
        assertEquals(roadB.getId(), crossing.otherRoadId());
        assertEquals(100.0, crossing.roadStation(), 1e-6);
        assertEquals(72.0, crossing.currentRoadElevation(), 1e-6);
        assertFalse(crossing.gradeSeparated());
    }

    @Test
    void resolvesGradeSeparatedCrossing() {
        RoadNetwork network = new RoadNetwork();
        RoadSystemConfig config = new RoadSystemConfig("test");
        Road roadA = network.createRoad("Highway");
        Road roadB = network.createRoad("Local");
        markEdited(roadA, List.of(
            PointOfVerticalIntersection.of(0, 76),
            PointOfVerticalIntersection.of(100, 76)));
        markEdited(roadB, List.of(
            PointOfVerticalIntersection.of(0, 68),
            PointOfVerticalIntersection.of(50, 68)));

        var west = network.createNode(new Vec2d(0, 0));
        var center = network.createNode(new Vec2d(50, 0));
        var east = network.createNode(new Vec2d(100, 0));
        var north = network.createNode(new Vec2d(50, 50));
        var south = network.createNode(new Vec2d(50, -50));
        RoadEdge mainWest = network.createEdge(
            west.getId(), center.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(50, 0)), roadA.getId());
        RoadEdge mainEast = network.createEdge(
            center.getId(), east.getId(),
            List.of(new Vec2d(50, 0), new Vec2d(100, 0)), roadA.getId());
        network.createEdge(
            center.getId(), north.getId(),
            List.of(new Vec2d(50, 0), new Vec2d(50, 50)), roadB.getId());
        network.createEdge(
            center.getId(), south.getId(),
            List.of(new Vec2d(50, 0), new Vec2d(50, -50)), roadB.getId());
        assertTrue(network.setNodeGradeSeparation(center.getId(), true, roadA.getId(), 4.0));

        RoadGenerationResult westProfile = profileSeries(
            List.of(0.0, 50.0),
            List.of(70, 70),
            List.of(71, 71),
            List.of(76, 76));
        RoadGenerationResult eastProfile = profileSeries(
            List.of(0.0, 50.0),
            List.of(70, 70),
            List.of(71, 71),
            List.of(76, 76));

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        edgeResults.put(mainWest.getId(), westProfile);
        edgeResults.put(mainEast.getId(), eastProfile);

        List<RoadProfileIntersection> intersections = RoadProfileIntersectionResolver.forRoad(
            network, roadA, config, edgeResults);

        assertEquals(1, intersections.size());
        RoadProfileIntersection crossing = intersections.getFirst();
        assertEquals(50.0, crossing.roadStation(), 1e-6);
        assertTrue(crossing.gradeSeparated());
        assertTrue(crossing.currentRoadElevated());
        assertEquals(4.0, crossing.clearance(), 1e-6);
    }

    private static void markEdited(Road road, List<PointOfVerticalIntersection> pvis) {
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setProfileSource(ProfileSource.EDITED);
        road.setVerticalAlignment(new RoadVerticalAlignment(pvis));
    }

    private static RoadGenerationResult profileSeries(
            List<Double> distances,
            List<Integer> ground,
            List<Integer> guide,
            List<Integer> build) {
        RoadGenerationResult profile = new RoadGenerationResult(100);
        profile.profileDistances = distances;
        profile.profileGroundHeights = ground;
        profile.profileGuideLine = guide;
        profile.profileBuildHeights = build;
        profile.profileDesignElevations = build.stream().map(Integer::doubleValue).toList();
        return profile;
    }
}
