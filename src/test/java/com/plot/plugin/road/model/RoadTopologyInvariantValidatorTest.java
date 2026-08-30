package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadTopologyInvariantValidatorTest {

    @Test
    void linearChainHasNoViolations() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-a");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());

        assertTrue(RoadTopologyInvariantValidator.validate(network).isEmpty());
    }

    @Test
    void detectsDisconnectedRoad() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-split");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        RoadNode n4 = network.createNode(new Vec2d(110, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n3.getId(), n4.getId(), List.of(new Vec2d(100, 0), new Vec2d(110, 0)), road.getId());

        List<RoadTopologyViolation> violations = RoadTopologyInvariantValidator.validateRoad(network, road);
        assertEquals(1, violations.size());
        assertEquals(RoadTopologyViolationKind.ROAD_DISCONNECTED, violations.getFirst().kind());
    }

    @Test
    void detectsBranchingRoad() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-fork");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        RoadNode d = network.createNode(new Vec2d(10, 10));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        network.createEdge(b.getId(), d.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());

        List<RoadTopologyViolation> violations = RoadTopologyInvariantValidator.validateRoad(network, road);
        assertEquals(1, violations.size());
        assertEquals(RoadTopologyViolationKind.ROAD_BRANCHING, violations.getFirst().kind());
    }

    @Test
    void detectsCycleOnImplicitLinearMode() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-loop");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(10, 10));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());
        network.createEdge(n3.getId(), n1.getId(), List.of(new Vec2d(10, 10), new Vec2d(0, 0)), road.getId());

        List<RoadTopologyViolation> violations = RoadTopologyInvariantValidator.validateRoad(network, road);
        assertEquals(1, violations.size());
        assertEquals(RoadTopologyViolationKind.ROAD_CYCLE, violations.getFirst().kind());
    }

    @Test
    void detectsOrderMismatchWithoutShapeViolation() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-a");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        RoadEdge edge1 = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        RoadEdge edge2 = network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        road.reorderSegments(List.of(edge2.getId(), edge1.getId()));

        List<RoadTopologyViolation> violations = RoadTopologyInvariantValidator.validateRoad(network, road);
        assertEquals(1, violations.size());
        assertEquals(RoadTopologyViolationKind.ROAD_ORDER_MISMATCH, violations.getFirst().kind());
    }

    @Test
    void loopTopologyModeAllowsCycleWithoutViolation() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-loop");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(10, 10));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());
        network.createEdge(n3.getId(), n1.getId(), List.of(new Vec2d(10, 10), new Vec2d(0, 0)), road.getId());

        assertTrue(RoadTopologyInvariantValidator.validateRoad(network, road).stream()
            .noneMatch(v -> v.kind() == RoadTopologyViolationKind.ROAD_CYCLE));
    }

    @Test
    void syncStorageOrderFixesOrderMismatch() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-a");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        RoadEdge edge1 = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        RoadEdge edge2 = network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        road.reorderSegments(List.of(edge2.getId(), edge1.getId()));

        assertTrue(RoadTopologyInvariantValidator.syncStorageOrderIfMaintainable(network, road));
        assertTrue(RoadTopologyInvariantValidator.validateRoad(network, road).isEmpty());
        assertEquals(List.of(edge1.getId(), edge2.getId()), road.getOrderedSegmentIds());
    }

    @Test
    void syncStorageOrderSkipsBranchingRoad() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-fork");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        RoadNode d = network.createNode(new Vec2d(10, 10));
        RoadEdge ab = network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        RoadEdge bc = network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        network.createEdge(b.getId(), d.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());
        road.reorderSegments(List.of(bc.getId(), ab.getId()));

        assertFalse(RoadTopologyInvariantValidator.syncStorageOrderIfMaintainable(network, road));
        assertEquals(RoadTopologyViolationKind.ROAD_BRANCHING,
            RoadTopologyInvariantValidator.validateRoad(network, road).getFirst().kind());
    }

    @Test
    void countByKindAggregatesAcrossRoads() {
        RoadNetwork network = new RoadNetwork();
        Road fork = network.createRoad("fork");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        RoadNode d = network.createNode(new Vec2d(10, 10));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), fork.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), fork.getId());
        network.createEdge(b.getId(), d.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), fork.getId());

        Road split = network.createRoad("split");
        RoadNode s1 = network.createNode(new Vec2d(100, 0));
        RoadNode s2 = network.createNode(new Vec2d(110, 0));
        RoadNode s3 = network.createNode(new Vec2d(200, 0));
        RoadNode s4 = network.createNode(new Vec2d(210, 0));
        network.createEdge(s1.getId(), s2.getId(), List.of(new Vec2d(100, 0), new Vec2d(110, 0)), split.getId());
        network.createEdge(s3.getId(), s4.getId(), List.of(new Vec2d(200, 0), new Vec2d(210, 0)), split.getId());

        Map<RoadTopologyViolationKind, Integer> counts = RoadTopologyInvariantValidator.countByKind(network);
        assertEquals(1, counts.get(RoadTopologyViolationKind.ROAD_BRANCHING));
        assertEquals(1, counts.get(RoadTopologyViolationKind.ROAD_DISCONNECTED));
        assertEquals(0, counts.get(RoadTopologyViolationKind.ROAD_CYCLE));
    }
}
