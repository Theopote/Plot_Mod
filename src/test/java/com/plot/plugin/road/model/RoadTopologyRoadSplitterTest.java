package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadTopologyRoadSplitterTest {

    private final RoadSystemConfig config = new RoadSystemConfig("test");
    private final RoadNetworkBuilder builder = new RoadNetworkBuilder();

    @Test
    void repairsDisconnectedComponents() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-split");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        RoadNode n4 = network.createNode(new Vec2d(110, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n3.getId(), n4.getId(), List.of(new Vec2d(100, 0), new Vec2d(110, 0)), road.getId());

        assertEquals(2, road.getOrderedSegmentIds().size());
        assertFalse(RoadTopologyInvariantValidator.validateRoad(network, road).isEmpty());

        RoadTopologyRoadSplitter.RepairResult result = RoadTopologyRoadSplitter.repairAfterAdopt(network);

        assertEquals(1, result.sourceRoadsRepaired());
        assertEquals(1, result.newRoadsCreated());
        assertEquals(2, network.getRoads().size());
        assertTrue(RoadTopologyInvariantValidator.validate(network).stream()
            .noneMatch(v -> v.kind() == RoadTopologyViolationKind.ROAD_DISCONNECTED));
    }

    @Test
    void splitsBranchingRoadAtFork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-fork");
        road.setName("Main");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        RoadNode d = network.createNode(new Vec2d(10, 10));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        network.createEdge(b.getId(), d.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());

        RoadTopologyRoadSplitter.RepairResult result = RoadTopologyRoadSplitter.repairAfterAdopt(network);

        assertEquals(1, result.sourceRoadsRepaired());
        assertEquals(2, result.newRoadsCreated());
        assertEquals(3, network.getRoads().size());
        assertTrue(RoadTopologyInvariantValidator.validate(network).stream()
            .noneMatch(v -> v.kind() == RoadTopologyViolationKind.ROAD_BRANCHING));

        long namedBranches = network.getRoads().values().stream()
            .filter(r -> r.getName() != null && r.getName().startsWith("Main · "))
            .count();
        assertEquals(2, namedBranches);
    }

    @Test
    void promotesClosedLoopToLoopMode() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("ring");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(10, 10));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());
        network.createEdge(n3.getId(), n1.getId(), List.of(new Vec2d(10, 10), new Vec2d(0, 0)), road.getId());

        RoadTopologyRoadSplitter.RepairResult result = RoadTopologyRoadSplitter.repairAfterAdopt(network);

        assertEquals(RoadTopologyMode.LOOP, road.getTopologyMode());
        assertEquals(1, result.loopsPromoted());
        assertTrue(RoadTopologyInvariantValidator.validateRoad(network, road).stream()
            .noneMatch(v -> v.kind() == RoadTopologyViolationKind.ROAD_CYCLE));
    }

    @Test
    void repairRoadPromotesClosedLoopOnly() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("ring");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(10, 10));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());
        network.createEdge(n3.getId(), n1.getId(), List.of(new Vec2d(10, 10), new Vec2d(0, 0)), road.getId());

        RoadTopologyRoadSplitter.RepairResult result = RoadTopologyRoadSplitter.repairRoad(network, road);

        assertEquals(RoadTopologyMode.LOOP, road.getTopologyMode());
        assertEquals(0, result.sourceRoadsRepaired());
        assertEquals(0, result.newRoadsCreated());
        assertEquals(1, result.loopsPromoted());
    }

    @Test
    void doesNotSplitValidTJunctionAcrossRoads() {
        RoadNetwork network = new RoadNetwork();
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 5), new Vec2d(5, 10)), false), config);

        int roadsBefore = network.getRoads().size();
        RoadTopologyRoadSplitter.RepairResult result = RoadTopologyRoadSplitter.repairAfterAdopt(network);

        assertEquals(0, result.newRoadsCreated());
        assertEquals(roadsBefore, network.getRoads().size());
        assertTrue(RoadTopologyInvariantValidator.validate(network).isEmpty());
    }

    @Test
    void repairRoadSplitsOnlyTargetRoad() {
        RoadNetwork network = new RoadNetwork();
        Road broken = network.createRoad("broken");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        RoadNode n4 = network.createNode(new Vec2d(110, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), broken.getId());
        network.createEdge(n3.getId(), n4.getId(), List.of(new Vec2d(100, 0), new Vec2d(110, 0)), broken.getId());

        Road healthy = network.createRoad("healthy");
        RoadNode h1 = network.createNode(new Vec2d(0, 50));
        RoadNode h2 = network.createNode(new Vec2d(10, 50));
        network.createEdge(h1.getId(), h2.getId(), List.of(new Vec2d(0, 50), new Vec2d(10, 50)), healthy.getId());

        RoadTopologyRoadSplitter.RepairResult result = RoadTopologyRoadSplitter.repairRoad(network, broken);

        assertEquals(1, result.sourceRoadsRepaired());
        assertEquals(1, result.newRoadsCreated());
        assertEquals(3, network.getRoads().size());
        assertTrue(RoadTopologyInvariantValidator.validateRoad(network, broken).isEmpty()
            || network.getRoad(broken.getId()) == null);
        assertTrue(RoadTopologyInvariantValidator.validateRoad(network, healthy).isEmpty());
    }

    @Test
    void repairsDisconnectedComponentsPreservesStationFacilitiesOnFirstChain() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-split");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        RoadNode n4 = network.createNode(new Vec2d(110, 0));
        String edgeNear = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId()).getId();
        network.createEdge(
            n3.getId(), n4.getId(), List.of(new Vec2d(100, 0), new Vec2d(110, 0)), road.getId());

        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(2.0, 8.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
        )));

        RoadTopologyRoadSplitter.repairAfterAdopt(network);

        Road keptRoad = network.getRoads().values().stream()
            .filter(candidate -> RoadSegmentOrdering.orderedSegmentIds(network, candidate).contains(edgeNear))
            .findFirst()
            .orElseThrow();
        assertNotNull(keptRoad.getStationFacilities());
        assertEquals(1, keptRoad.getStationFacilities().runCount());
        assertEquals(2.0, keptRoad.getStationFacilities().sortedRuns().getFirst().getStartStation(), 1e-6);

        Road farRoad = network.getRoads().values().stream()
            .filter(candidate -> candidate != keptRoad)
            .findFirst()
            .orElseThrow();
        assertNull(farRoad.getStationFacilities());
        assertEquals(edgeNear, RoadSegmentOrdering.orderedSegmentIds(network, keptRoad).getFirst());
    }

    @Test
    void splitsBranchingRoadPreservesStationDataOnBranch() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-fork");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        RoadNode d = network.createNode(new Vec2d(10, 10));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        String edgeBc = network.createEdge(
            b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId()).getId();
        network.createEdge(b.getId(), d.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());

        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(10.0, 10.0),
            PointOfVerticalIntersection.of(20.0, 12.0)
        )));
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(12.0, 18.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.RIGHT)
        )));

        RoadTopologyRoadSplitter.repairAfterAdopt(network);

        Road branchRoad = network.getRoads().values().stream()
            .filter(candidate -> candidate.getOrderedSegmentIds().contains(edgeBc))
            .findFirst()
            .orElseThrow();

        assertNotNull(branchRoad.getStationFacilities());
        StationFacilityRun run = branchRoad.getStationFacilities().sortedRuns().getFirst();
        assertEquals(2.0, run.getStartStation(), 1e-6);
        assertEquals(8.0, run.getEndStation(), 1e-6);
        assertNotNull(branchRoad.getVerticalAlignment());
        assertEquals(2, branchRoad.getVerticalAlignment().sortedPvis().size());
        assertEquals(0.0, branchRoad.getVerticalAlignment().sortedPvis().getFirst().getStation(), 1e-6);
        assertEquals(10.0, branchRoad.getVerticalAlignment().sortedPvis().getLast().getStation(), 1e-6);
    }
}
