package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拓扑编辑回归矩阵（T01–T06）：Adopt / Split / Merge / Delete / Repair / Undo-Redo。
 */
class RoadTopologyEditRegressionTest {
    private RoadNetworkManager manager;
    private RoadNetwork network;

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("topology-edit"), new RoadProjectStatus());
        network = manager.getNetwork();
    }

    @Test
    void t01AdoptSimpleChain() {
        manager.adoptSelectedPaths(List.of(new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(20, 0), new Vec2d(30, 0)), false)));

        assertEquals(1, network.getRoads().size());
        Road road = network.getRoads().values().iterator().next();
        assertEquals(1, road.getOrderedSegmentIds().size());
        assertTrue(RoadTopologyInvariantValidator.validate(network).isEmpty());
        assertTrue(network.validateInvariants().isValid());
    }

    @Test
    void t02SplitRoad() {
        linearRoadFourSegments();
        Road road = singleRoad();
        String splitSegmentId = road.getOrderedSegmentIds().get(2);

        String newRoadId = manager.splitRoadBeforeSegment(road.getId(), splitSegmentId);
        assertNotNull(newRoadId);

        Road head = network.getRoad(road.getId());
        Road tail = network.getRoad(newRoadId);
        assertEquals(2, head.getOrderedSegmentIds().size());
        assertEquals(2, tail.getOrderedSegmentIds().size());
        assertTrue(RoadTopologyInvariantValidator.validateRoad(network, head).isEmpty());
        assertTrue(RoadTopologyInvariantValidator.validateRoad(network, tail).isEmpty());
        assertTrue(network.validateInvariants().isValid());
    }

    @Test
    void t03MergeRoads() {
        linearRoadFourSegments();
        Road road = singleRoad();
        String splitSegmentId = road.getOrderedSegmentIds().get(2);
        String tailRoadId = manager.splitRoadBeforeSegment(road.getId(), splitSegmentId);
        assertNotNull(tailRoadId);

        String mergedId = network.mergeRoadTailIntoHead(road.getId(), tailRoadId);
        assertEquals(road.getId(), mergedId);
        assertEquals(4, road.getOrderedSegmentIds().size());
        assertNull(network.getRoad(tailRoadId));
        assertTrue(RoadTopologyInvariantValidator.validateRoad(network, road).isEmpty());
        assertTrue(network.validateInvariants().isValid());
    }

    @Test
    void t04DeleteMiddleEdgeSplitsIntoIndependentRoads() {
        linearRoadFourSegments();
        Road road = singleRoad();
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64),
            PointOfVerticalIntersection.of(5, 66),
            PointOfVerticalIntersection.of(25, 70),
            PointOfVerticalIntersection.of(35, 68),
            PointOfVerticalIntersection.of(40, 64))));
        String middleEdgeId = road.getOrderedSegmentIds().get(1);

        manager.deleteEdge(middleEdgeId);
        refreshNetwork();

        // Middle delete must not silently stitch remaining segments into one linear road.
        assertNull(network.getEdge(middleEdgeId));
        assertTrue(network.getRoads().size() >= 2);
        assertTrue(RoadTopologyInvariantValidator.validate(network).stream()
            .noneMatch(v -> v.kind() == RoadTopologyViolationKind.ROAD_DISCONNECTED));
        for (Road surviving : network.getRoads().values()) {
            assertTrue(RoadTopologyInvariantValidator.validateRoad(network, surviving).isEmpty());
            assertTrue(RoadStationing.canonicalLength(network, surviving) > 0);
            if (surviving.getVerticalAlignment() != null) {
                double length = RoadStationing.canonicalLength(network, surviving);
                for (PointOfVerticalIntersection pvi : surviving.getVerticalAlignment().getPvis()) {
                    assertTrue(pvi.getStation() >= -1e-6);
                    assertTrue(pvi.getStation() <= length + 1e-6);
                }
            }
        }
    }

    @Test
    void deleteHeadSegmentRebasesDownstreamPvis() {
        linearRoadThreeSegments();
        Road road = singleRoad();
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64),
            PointOfVerticalIntersection.of(15, 70),
            PointOfVerticalIntersection.of(25, 68),
            PointOfVerticalIntersection.of(30, 64))));
        String headEdgeId = road.getOrderedSegmentIds().getFirst();

        manager.deleteEdge(headEdgeId);
        refreshNetwork();
        Road surviving = singleRoad();
        assertEquals(20.0, RoadStationing.canonicalLength(network, surviving), 1e-6);
        List<PointOfVerticalIntersection> pvis = surviving.getVerticalAlignment().sortedPvis();
        assertEquals(5.0, pvis.stream().filter(p -> Math.abs(p.getElevation() - 70) < 1e-6)
            .findFirst().orElseThrow().getStation(), 1e-6);
        assertEquals(15.0, pvis.stream().filter(p -> Math.abs(p.getElevation() - 68) < 1e-6)
            .findFirst().orElseThrow().getStation(), 1e-6);
    }

    @Test
    void deleteTailSegmentTrimsPastEndPvis() {
        linearRoadThreeSegments();
        Road road = singleRoad();
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64),
            PointOfVerticalIntersection.of(10, 70),
            PointOfVerticalIntersection.of(25, 68),
            PointOfVerticalIntersection.of(30, 64))));
        String tailEdgeId = road.getOrderedSegmentIds().getLast();

        manager.deleteEdge(tailEdgeId);
        refreshNetwork();
        Road surviving = singleRoad();
        assertEquals(20.0, RoadStationing.canonicalLength(network, surviving), 1e-6);
        for (PointOfVerticalIntersection pvi : surviving.getVerticalAlignment().getPvis()) {
            assertTrue(pvi.getStation() <= 20.0 + 1e-6);
        }
        assertTrue(surviving.getVerticalAlignment().getPvis().stream()
            .anyMatch(p -> Math.abs(p.getElevation() - 70) < 1e-6));
    }

    @Test
    void t05JunctionRepairMaintainsBidirectionalReferences() {
        Road road = network.createRoad("fork");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        RoadNode d = network.createNode(new Vec2d(10, 10));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(b.getPosition(), c.getPosition()), road.getId());
        network.createEdge(b.getId(), d.getId(), List.of(b.getPosition(), d.getPosition()), road.getId());

        RoadTopologyRoadSplitter.repairAfterAdopt(network);
        assertTrue(RoadTopologyInvariantValidator.validate(network).stream()
            .noneMatch(v -> v.kind() == RoadTopologyViolationKind.ROAD_BRANCHING));

        for (RoadEdge edge : network.getEdges().values()) {
            assertTrue(network.getNode(edge.getStartNodeId()).getConnectedEdgeIds().contains(edge.getId()));
            assertTrue(network.getNode(edge.getEndNodeId()).getConnectedEdgeIds().contains(edge.getId()));
            if (edge.getRoadId() != null) {
                assertTrue(network.getRoad(edge.getRoadId()).getSegmentIds().contains(edge.getId()));
            }
        }
        assertTrue(network.validateInvariants().isValid());
    }

    @Test
    void t06UndoRedoPreserveInvariants() {
        linearRoadFourSegments();
        Road road = singleRoad();
        int before = road.getOrderedSegmentIds().size();

        String newRoadId = manager.splitRoadBeforeSegment(road.getId(), road.getOrderedSegmentIds().get(2));
        assertNotNull(newRoadId);
        refreshNetwork();
        assertTrue(network.validateInvariants().isValid());

        manager.undo();
        refreshNetwork();
        assertEquals(before, singleRoad().getOrderedSegmentIds().size());
        assertTrue(network.validateInvariants().isValid());

        manager.redo();
        refreshNetwork();
        assertEquals(2, network.getRoads().size());
        assertTrue(network.validateInvariants().isValid());
    }

    private void refreshNetwork() {
        network = manager.getNetwork();
    }

    private void linearRoadFourSegments() {
        Road road = network.createRoad("chain");
        RoadNode n0 = network.createNode(new Vec2d(0, 0));
        RoadNode n1 = network.createNode(new Vec2d(10, 0));
        RoadNode n2 = network.createNode(new Vec2d(20, 0));
        RoadNode n3 = network.createNode(new Vec2d(30, 0));
        RoadNode n4 = network.createNode(new Vec2d(40, 0));
        network.createEdge(n0.getId(), n1.getId(), List.of(n0.getPosition(), n1.getPosition()), road.getId());
        network.createEdge(n1.getId(), n2.getId(), List.of(n1.getPosition(), n2.getPosition()), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(n2.getPosition(), n3.getPosition()), road.getId());
        network.createEdge(n3.getId(), n4.getId(), List.of(n3.getPosition(), n4.getPosition()), road.getId());
    }

    private void linearRoadThreeSegments() {
        Road road = network.createRoad("chain3");
        RoadNode n0 = network.createNode(new Vec2d(0, 0));
        RoadNode n1 = network.createNode(new Vec2d(10, 0));
        RoadNode n2 = network.createNode(new Vec2d(20, 0));
        RoadNode n3 = network.createNode(new Vec2d(30, 0));
        network.createEdge(n0.getId(), n1.getId(), List.of(n0.getPosition(), n1.getPosition()), road.getId());
        network.createEdge(n1.getId(), n2.getId(), List.of(n1.getPosition(), n2.getPosition()), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(n2.getPosition(), n3.getPosition()), road.getId());
    }

    private Road singleRoad() {
        assertEquals(1, network.getRoads().size());
        return network.getRoads().values().iterator().next();
    }
}
