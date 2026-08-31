package com.plot.plugin.road.model.facility;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.StationFacilityPersistence;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationFacilityResolverTest {

    @Test
    void activeAtUsesOpenEndedRunToRoadEnd() {
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        Road road = network.createRoad("r1");
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(40.0, null, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
        )));

        assertTrue(StationFacilityResolver.activeAt(network, road, 40.0).stream()
            .anyMatch(run -> run.getKind() == RoadFacilityKind.GUARDRAIL));
        assertTrue(StationFacilityResolver.activeAt(network, road, 99.0).stream()
            .anyMatch(run -> run.getKind() == RoadFacilityKind.GUARDRAIL));
        assertTrue(StationFacilityResolver.activeAt(network, road, 39.9).isEmpty());
    }

    @Test
    void activeAtRespectsClosedEndStation() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(10.0, 30.0, RoadFacilityKind.DRAINAGE, RoadFacilitySide.BOTH)
        )));

        assertFalse(StationFacilityResolver.hasActiveKind(network, road, 9.0, RoadFacilityKind.DRAINAGE));
        assertTrue(StationFacilityResolver.hasActiveKind(network, road, 20.0, RoadFacilityKind.DRAINAGE));
        assertTrue(StationFacilityResolver.hasActiveKind(network, road, 30.0, RoadFacilityKind.DRAINAGE));
        assertFalse(StationFacilityResolver.hasActiveKind(network, road, 30.1, RoadFacilityKind.DRAINAGE));
    }

    @Test
    void activeOnEdgeReturnsOverlappingRuns() {
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(60, 0));
        RoadNode n3 = network.createNode(new Vec2d(120, 0));
        Road road = network.createRoad("r1");
        RoadEdge first = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(60, 0)), road.getId());
        RoadEdge second = network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(60, 0), new Vec2d(120, 0)), road.getId());
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(20.0, 50.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.RIGHT),
            StationFacilityRun.of(70.0, null, RoadFacilityKind.RETAINING_WALL, RoadFacilitySide.LEFT)
        )));

        List<StationFacilityRun> onFirst = StationFacilityResolver.activeOnEdge(network, road, first);
        assertEquals(1, onFirst.size());
        assertEquals(RoadFacilityKind.GUARDRAIL, onFirst.getFirst().getKind());

        List<StationFacilityRun> onSecond = StationFacilityResolver.activeOnEdge(network, road, second);
        assertEquals(1, onSecond.size());
        assertEquals(RoadFacilityKind.RETAINING_WALL, onSecond.getFirst().getKind());
    }

    @Test
    void activeKindsAtCollectsMultipleRuns() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(0.0, 100.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT),
            StationFacilityRun.of(0.0, 100.0, RoadFacilityKind.DRAINAGE, RoadFacilitySide.BOTH)
        )));

        EnumSet<RoadFacilityKind> kinds = StationFacilityResolver.activeKindsAt(network, road, 50.0);
        assertTrue(kinds.contains(RoadFacilityKind.GUARDRAIL));
        assertTrue(kinds.contains(RoadFacilityKind.DRAINAGE));
    }

    @Test
    void usesStationGatedDrainageWhenDrainageRunsExist() {
        Road road = new Road("r1");
        assertFalse(StationFacilityResolver.usesStationGatedDrainage(road));

        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(0.0, 10.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
        )));
        assertFalse(StationFacilityResolver.usesStationGatedDrainage(road));

        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(0.0, 10.0, RoadFacilityKind.DRAINAGE, RoadFacilitySide.BOTH)
        )));
        assertTrue(StationFacilityResolver.usesStationGatedDrainage(road));
    }

    @Test
    void persistenceRoundTrip() {
        RoadStationFacilities facilities = new RoadStationFacilities(List.of(
            new StationFacilityRun(
                25.0, 80.0, RoadFacilityKind.RETAINING_WALL, RoadFacilitySide.RIGHT, "stone_bricks", 3.0),
            StationFacilityRun.of(90.0, null, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.BOTH)
        ));

        RoadStationFacilities restored = StationFacilityPersistence.fromData(
            StationFacilityPersistence.toData(facilities));

        assertNotNull(restored);
        assertEquals(2, restored.runCount());
        StationFacilityRun wall = restored.sortedRuns().getFirst();
        assertEquals(RoadFacilityKind.RETAINING_WALL, wall.getKind());
        assertEquals(3.0, wall.getHeight());
        assertEquals("stone_bricks", wall.getMaterial());
    }

    @Test
    void jsonRoundTripThroughNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("fac");
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(15.0, 45.0, RoadFacilityKind.DRAINAGE, RoadFacilitySide.BOTH)
        )));

        RoadNetwork restored = RoadNetwork.parseSnapshot(network.toJson());
        Road restoredRoad = restored.getRoad("fac");

        assertNotNull(restoredRoad.getStationFacilities());
        assertEquals(1, restoredRoad.getStationFacilities().runCount());
        assertEquals(
            RoadFacilityKind.DRAINAGE,
            restoredRoad.getStationFacilities().sortedRuns().getFirst().getKind());
    }
}
