package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import com.plot.plugin.road.RoadNetworkEngineeringValidator;

class VerticalProfileNetworkPropagatorTest {
    @Test void dispatchesSharedJunctionChangeByVerticalModeAndBlocksFlatConflict() {
        RoadNetwork network = new RoadNetwork();
        var junction = network.createNode(new Vec2d(0, 0));

        Road source = roadFromJunction(network, junction, "source", new Vec2d(100, 0));
        source.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        source.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 76),
            PointOfVerticalIntersection.of(100, 76))));

        Road automatic = roadFromJunction(network, junction, "auto", new Vec2d(0, 100));
        automatic.setVerticalMode(RoadVerticalMode.AUTO_SMOOTH);
        Road terrain = roadFromJunction(network, junction, "terrain", new Vec2d(-100, 0));
        terrain.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        Road flat = roadFromJunction(network, junction, "flat", new Vec2d(0, -100));
        flat.setVerticalMode(RoadVerticalMode.FLAT);
        flat.setVerticalAlignment(VerticalProfileDesignRules.flatAlignment(100, 70));

        VerticalProfileNetworkPropagator.Result result =
            VerticalProfileNetworkPropagator.propagate(network, source, ignored -> 8.0);

        assertEquals(76, junction.getManualElevation(), 1e-6);
        assertTrue(result.roads().stream().anyMatch(item ->
            item.roadId().equals(automatic.getId()) && item.regenerationRequired()
                && item.fullyResolved()));
        assertTrue(result.roads().stream().anyMatch(item ->
            item.roadId().equals(terrain.getId()) && item.regenerationRequired()
                && item.fullyResolved()));
        assertTrue(result.roads().stream().anyMatch(item ->
            item.roadId().equals(flat.getId()) && !item.changed()
                && !item.fullyResolved()));
        assertTrue(RoadNetworkEngineeringValidator.analyzePreGeneration(network).blocksBuild());

        assertEquals(1, FlatRoadJunctionConflictResolver.allowConflictingRoadsToSlope(network));
        assertEquals(RoadVerticalMode.MANUAL_PROFILE, flat.getVerticalMode());
        assertTrue(flat.getVerticalAlignment().getPvis().stream()
            .anyMatch(pvi -> Math.abs(pvi.getElevation() - 76) < 1e-6));
        assertFalse(RoadNetworkEngineeringValidator.analyzePreGeneration(network).blocksBuild());
    }

    private static Road roadFromJunction(
            RoadNetwork network,
            com.plot.plugin.road.model.RoadNode junction,
            String id,
            Vec2d endPosition) {
        Road road = network.createRoad(id);
        var end = network.createNode(endPosition);
        network.createEdge(junction.getId(), end.getId(),
            List.of(new Vec2d(0, 0), endPosition), road.getId());
        return road;
    }

    @Test void sharedJunctionMovesFreeGradePointsOnConnectedRoad() {
        RoadNetwork network = new RoadNetwork();
        Road eastWest = network.createRoad("east-west");
        Road northSouth = network.createRoad("north-south");
        eastWest.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(100, 76),
            PointOfVerticalIntersection.of(200, 70))));
        northSouth.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(60, 70),
            PointOfVerticalIntersection.of(140, 70),
            PointOfVerticalIntersection.of(200, 70))));
        var west = network.createNode(new Vec2d(-100, 0));
        var junction = network.createNode(new Vec2d(0, 0));
        var east = network.createNode(new Vec2d(100, 0));
        var south = network.createNode(new Vec2d(0, -100));
        var north = network.createNode(new Vec2d(0, 100));
        network.createEdge(west.getId(), junction.getId(),
            List.of(new Vec2d(-100, 0), new Vec2d(0, 0)), eastWest.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)), eastWest.getId());
        network.createEdge(south.getId(), junction.getId(),
            List.of(new Vec2d(0, -100), new Vec2d(0, 0)), northSouth.getId());
        network.createEdge(junction.getId(), north.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, 100)), northSouth.getId());

        VerticalProfileNetworkPropagator.Result result =
            VerticalProfileNetworkPropagator.propagate(network, eastWest, road -> 8.0);

        assertEquals(0, result.unresolvedRoadCount());
        assertTrue(result.adjustedRoadCount() >= 1);
        List<PointOfVerticalIntersection> pvis = northSouth.getVerticalAlignment().getPvis();
        assertEquals(5, pvis.size());
        assertEquals(25, pvis.get(1).getStation(), 1e-6);
        assertEquals(100, pvis.get(2).getStation(), 1e-6);
        assertEquals(76, pvis.get(2).getElevation(), 1e-6);
        assertEquals(175, pvis.get(3).getStation(), 1e-6);
    }

    @Test void reportsUnresolvedWhenOnlyFixedEndpointsAreAvailable() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(20, 80))));
        var junction = network.createNode(new Vec2d(0, 0));
        var end = network.createNode(new Vec2d(20, 0));
        var n = network.createNode(new Vec2d(0, 10));
        var s = network.createNode(new Vec2d(0, -10));
        network.createEdge(junction.getId(), end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(20, 0)), road.getId());
        network.createEdge(junction.getId(), n.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 10)));
        network.createEdge(junction.getId(), s.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, -10)));
        VerticalProfileNetworkPropagator.Result result =
            VerticalProfileNetworkPropagator.propagate(network, road, ignored -> 8.0);
        assertTrue(result.unresolvedRoadCount() >= 1);
    }
}
