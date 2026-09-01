package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VerticalAlignmentJunctionSynchronizerTest {
    @Test void sharesEndpointElevationAtAtGradeJunction() {
        Fixture f = fixture(false);
        assertEquals(1, VerticalAlignmentJunctionSynchronizer.synchronize(f.network, f.road));
        assertEquals(76.0, f.junction.getManualElevation(), 1e-6);
        assertEquals(VerticalControlPointConstraint.JUNCTION_FIXED,
            f.road.getVerticalAlignment().getPvis().getFirst().getConstraint());
    }

    @Test void doesNotCollapseGradeSeparatedCrossingLayers() {
        Fixture f = fixture(true);
        assertEquals(0, VerticalAlignmentJunctionSynchronizer.synchronize(f.network, f.road));
        assertNull(f.junction.getManualElevation());
    }

    @Test void internalJunctionPviPropagatesIntoConnectedManualRoad() {
        RoadNetwork network = new RoadNetwork();
        Road eastWest = network.createRoad("east-west");
        Road northSouth = network.createRoad("north-south");
        eastWest.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(20, 76),
            PointOfVerticalIntersection.of(40, 72))));
        northSouth.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 68),
            PointOfVerticalIntersection.of(40, 69))));
        var west = network.createNode(new Vec2d(-20, 0));
        var junction = network.createNode(new Vec2d(0, 0));
        var east = network.createNode(new Vec2d(20, 0));
        var north = network.createNode(new Vec2d(0, 20));
        var south = network.createNode(new Vec2d(0, -20));
        network.createEdge(west.getId(), junction.getId(),
            List.of(new Vec2d(-20, 0), new Vec2d(0, 0)), eastWest.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(20, 0)), eastWest.getId());
        network.createEdge(south.getId(), junction.getId(),
            List.of(new Vec2d(0, -20), new Vec2d(0, 0)), northSouth.getId());
        network.createEdge(junction.getId(), north.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, 20)), northSouth.getId());

        assertTrue(VerticalAlignmentJunctionSynchronizer.synchronize(network, eastWest) >= 2);
        assertEquals(76, junction.getManualElevation(), 1e-6);
        assertEquals(3, northSouth.getVerticalAlignment().pviCount());
        PointOfVerticalIntersection shared = northSouth.getVerticalAlignment().getPvis().stream()
            .filter(pvi -> Math.abs(pvi.getStation() - 20) < 1e-6)
            .findFirst().orElseThrow();
        assertEquals(76, shared.getElevation(), 1e-6);
        assertEquals(VerticalControlPointConstraint.JUNCTION_FIXED, shared.getConstraint());
        assertTrue(VerticalAlignmentJunctionSynchronizer.isSharedJunctionAtStation(
            network, northSouth, 20));
    }

    private static Fixture fixture(boolean gradeSeparated) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 76.0),
            PointOfVerticalIntersection.of(20.0, 78.0))));
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(20, 0));
        RoadNode north = network.createNode(new Vec2d(0, 20));
        RoadNode south = network.createNode(new Vec2d(0, -20));
        network.createEdge(junction.getId(), end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(20, 0)), road.getId());
        network.createEdge(junction.getId(), north.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, 20)));
        network.createEdge(junction.getId(), south.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, -20)));
        junction.setGradeSeparated(gradeSeparated);
        return new Fixture(network, road, junction);
    }

    private record Fixture(RoadNetwork network, Road road, RoadNode junction) { }
}
