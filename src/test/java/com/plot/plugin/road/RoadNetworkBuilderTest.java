package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.graph.RoadGraphEdits;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadSegmentOrdering;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkBuilderTest {

    private final RoadNetworkBuilder builder = new RoadNetworkBuilder();
    private final RoadSystemConfig config = new RoadSystemConfig("road_system");

    @Test
    void splitSlopeOverridesRemapsMileage() {
        List<RoadEdge.SlopeOverride> overrides = List.of(
            new RoadEdge.SlopeOverride(10, 20, 3.0f)
        );

        List<RoadEdge.SlopeOverride> first = RoadGraphEdits.splitSlopeOverrides(overrides, 15, 30, true);
        assertEquals(1, first.size());
        assertEquals(10, first.getFirst().startDistance, 1e-6);
        assertEquals(15, first.getFirst().endDistance, 1e-6);
        assertEquals(3.0f, first.getFirst().maxSlope);

        List<RoadEdge.SlopeOverride> second = RoadGraphEdits.splitSlopeOverrides(overrides, 15, 30, false);
        assertEquals(1, second.size());
        assertEquals(0, second.getFirst().startDistance, 1e-6);
        assertEquals(5, second.getFirst().endDistance, 1e-6);
        assertEquals(3.0f, second.getFirst().maxSlope);
    }

    @Test
    void splitSlopeOverridesDropsOutOfRangeSegments() {
        List<RoadEdge.SlopeOverride> overrides = List.of(
            new RoadEdge.SlopeOverride(0, 5, 2.0f),
            new RoadEdge.SlopeOverride(25, 30, 4.0f)
        );

        List<RoadEdge.SlopeOverride> first = RoadGraphEdits.splitSlopeOverrides(overrides, 15, 30, true);
        assertEquals(1, first.size());
        assertEquals(0, first.getFirst().startDistance, 1e-6);
        assertEquals(5, first.getFirst().endDistance, 1e-6);

        List<RoadEdge.SlopeOverride> second = RoadGraphEdits.splitSlopeOverrides(overrides, 15, 30, false);
        assertEquals(1, second.size());
        assertEquals(10, second.getFirst().startDistance, 1e-6);
        assertEquals(15, second.getFirst().endDistance, 1e-6);
    }

    @Test
    void adoptEndpointToMiddleCreatesTJunction() {
        RoadNetwork network = new RoadNetwork();

        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 5), new Vec2d(5, 10)), false), config);

        assertEquals(3, network.getEdges().size());
        assertEquals(4, network.getNodes().size());
        assertEquals(1, network.getJunctionCount());

        RoadNode junction = findNodeNear(network, new Vec2d(5, 5));
        assertNotNull(junction);
        assertEquals(3, junction.getDegree());
        assertEquals(RoadNetworkBuilder.JunctionType.T_JUNCTION, builder.classify(junction));
    }

    @Test
    void adoptMiddleToMiddleCreatesCrossroad() {
        RoadNetwork network = new RoadNetwork();

        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false), config);

        assertEquals(4, network.getEdges().size());
        assertEquals(5, network.getNodes().size());
        assertEquals(1, network.getJunctionCount());

        RoadNode junction = findNodeNear(network, new Vec2d(5, 5));
        assertNotNull(junction);
        assertEquals(4, junction.getDegree());
        assertEquals(RoadNetworkBuilder.JunctionType.CROSSROAD, builder.classify(junction));
    }

    @Test
    void adoptEndpointToEndpointMergesNodes() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");

        RoadNode aStart = network.createNode(new Vec2d(0, 0));
        RoadNode aEnd = network.createNode(new Vec2d(10, 0));
        RoadNode bStart = network.createNode(new Vec2d(10.01, 0));
        RoadNode bEnd = network.createNode(new Vec2d(20, 0));

        network.createEdge(aStart.getId(), aEnd.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(10, 0)), roadA.getId());
        network.createEdge(bStart.getId(), bEnd.getId(), List.of(
            new Vec2d(10.01, 0), new Vec2d(20, 0)), roadB.getId());

        builder.detectAndSplitIntersections(network);

        assertEquals(2, network.getEdges().size());
        assertEquals(3, network.getNodes().size());

        RoadNode shared = findNodeNear(network, new Vec2d(10, 0));
        assertNotNull(shared);
        assertEquals(2, shared.getDegree());
    }

    @Test
    void nearEndpointWithinToleranceDoesNotCreateDuplicateNode() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");

        RoadNode aStart = network.createNode(new Vec2d(0, 5));
        RoadNode aEnd = network.createNode(new Vec2d(10, 5));
        RoadNode bStart = network.createNode(new Vec2d(5, 5.01));
        RoadNode bEnd = network.createNode(new Vec2d(5, 10));

        network.createEdge(aStart.getId(), aEnd.getId(), List.of(
            new Vec2d(0, 5), new Vec2d(10, 5)), roadA.getId());
        network.createEdge(bStart.getId(), bEnd.getId(), List.of(
            new Vec2d(5, 5.01), new Vec2d(5, 10)), roadB.getId());

        builder.detectAndSplitIntersections(network);

        assertEquals(3, network.getEdges().size());
        assertEquals(4, network.getNodes().size());

        long nodesNearJunction = network.getNodes().values().stream()
            .filter(node -> RoadGeometryUtils.pointsNear(node.getPosition(), new Vec2d(5, 5), RoadNetworkBuilder.NODE_TOLERANCE))
            .count();
        assertEquals(1, nodesNearJunction);
    }

    @Test
    void detectAndSplitIntersectionsCompletesForCascadeIntersections() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");
        Road roadC = network.createRoad("road-c");

        RoadNode aStart = network.createNode(new Vec2d(0, 5));
        RoadNode aEnd = network.createNode(new Vec2d(20, 5));
        network.createEdge(aStart.getId(), aEnd.getId(), List.of(
            new Vec2d(0, 5), new Vec2d(20, 5)), roadA.getId());

        RoadNode bStart = network.createNode(new Vec2d(5, 5));
        RoadNode bEnd = network.createNode(new Vec2d(5, 10));
        network.createEdge(bStart.getId(), bEnd.getId(), List.of(
            new Vec2d(5, 5), new Vec2d(5, 10)), roadB.getId());

        RoadNode cStart = network.createNode(new Vec2d(15, 5));
        RoadNode cEnd = network.createNode(new Vec2d(15, 10));
        network.createEdge(cStart.getId(), cEnd.getId(), List.of(
            new Vec2d(15, 5), new Vec2d(15, 10)), roadC.getId());

        IntersectionResult result = builder.detectAndSplitIntersections(network);

        assertEquals(IntersectionResult.COMPLETE, result);
        assertEquals(5, network.getEdges().size());
        assertEquals(2, network.getJunctionCount());

        Set<String> roadASegments = network.getRoad(roadA.getId()).getSegmentIds();
        assertEquals(3, roadASegments.size());
    }

    @Test
    void detectAndSplitIntersectionsSyncsRoadSegmentOrder() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-a");
        RoadNode n1 = network.createNode(new Vec2d(0, 5));
        RoadNode n2 = network.createNode(new Vec2d(10, 5));
        RoadNode n3 = network.createNode(new Vec2d(20, 5));
        RoadNode n4 = network.createNode(new Vec2d(30, 5));
        RoadEdge e1 = network.createEdge(n1.getId(), n2.getId(), List.of(
            new Vec2d(0, 5), new Vec2d(10, 5)), road.getId());
        RoadEdge e2 = network.createEdge(n2.getId(), n3.getId(), List.of(
            new Vec2d(10, 5), new Vec2d(20, 5)), road.getId());
        RoadEdge e3 = network.createEdge(n3.getId(), n4.getId(), List.of(
            new Vec2d(20, 5), new Vec2d(30, 5)), road.getId());

        Road roadB = network.createRoad("road-b");
        RoadNode bStart = network.createNode(new Vec2d(15, 5));
        RoadNode bEnd = network.createNode(new Vec2d(15, 10));
        network.createEdge(bStart.getId(), bEnd.getId(), List.of(
            new Vec2d(15, 5), new Vec2d(15, 10)), roadB.getId());

        road.reorderSegments(List.of(e3.getId(), e1.getId(), e2.getId()));

        IntersectionResult result = builder.detectAndSplitIntersections(network);
        assertEquals(IntersectionResult.COMPLETE, result);

        Road syncedRoad = network.getRoad(road.getId());
        assertEquals(
            RoadSegmentOrdering.orderedSegmentIds(network, syncedRoad),
            syncedRoad.getOrderedSegmentIds());
        assertEquals(4, syncedRoad.getOrderedSegmentIds().size());

        List<String> ids = syncedRoad.getOrderedSegmentIds();
        RoadEdge first = network.getEdge(ids.get(0));
        RoadEdge second = network.getEdge(ids.get(1));
        assertEquals(n1.getId(), first.getStartNodeId());
        assertEquals(second.getStartNodeId(), first.getEndNodeId());
    }

    @Test
    void detectAndSplitIntersectionsReportsIncompleteWhenPassLimitReached() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");
        Road roadC = network.createRoad("road-c");

        RoadNode aStart = network.createNode(new Vec2d(0, 5));
        RoadNode aEnd = network.createNode(new Vec2d(20, 5));
        network.createEdge(aStart.getId(), aEnd.getId(), List.of(
            new Vec2d(0, 5), new Vec2d(20, 5)), roadA.getId());

        RoadNode bStart = network.createNode(new Vec2d(5, 5));
        RoadNode bEnd = network.createNode(new Vec2d(5, 10));
        network.createEdge(bStart.getId(), bEnd.getId(), List.of(
            new Vec2d(5, 5), new Vec2d(5, 10)), roadB.getId());

        RoadNode cStart = network.createNode(new Vec2d(15, 5));
        RoadNode cEnd = network.createNode(new Vec2d(15, 10));
        network.createEdge(cStart.getId(), cEnd.getId(), List.of(
            new Vec2d(15, 5), new Vec2d(15, 10)), roadC.getId());

        IntersectionResult result = builder.detectAndSplitIntersections(network, null, 1);

        assertEquals(IntersectionResult.INCOMPLETE, result);
        assertTrue(network.getEdges().size() < 5);
    }

    @Test
    void adoptShapePropagatesIntersectionResult() {
        RoadNetwork network = new RoadNetwork();

        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        RoadNetworkBuilder.AdoptResult result = builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 5), new Vec2d(5, 10)), false), config);

        assertEquals(IntersectionResult.COMPLETE, result.intersectionResult());
    }

    @Test
    void intersectionPreservesRoadMembership() {
        RoadNetwork network = new RoadNetwork();

        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        RoadNetworkBuilder.AdoptResult result = builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 5), new Vec2d(5, 10)), false), config);

        String roadBId = result.edges().getFirst().getRoadId();
        Road roadB = network.getRoad(roadBId);
        assertNotNull(roadB);

        Set<String> roadBSegmentIds = new HashSet<>(roadB.getSegmentIds());
        assertEquals(1, roadBSegmentIds.size());

        for (RoadEdge edge : network.getEdges().values()) {
            if (roadBId.equals(edge.getRoadId())) {
                assertTrue(roadBSegmentIds.contains(edge.getId()));
            }
        }

        long roadAEdges = network.getEdges().values().stream()
            .filter(edge -> !roadBId.equals(edge.getRoadId()))
            .count();
        assertEquals(2, roadAEdges);
    }

    @Test
    void intersectionPreservesSlopeOverrides() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");

        RoadNode aStart = network.createNode(new Vec2d(0, 5));
        RoadNode aEnd = network.createNode(new Vec2d(10, 5));
        RoadEdge edgeA = network.createEdge(aStart.getId(), aEnd.getId(), List.of(
            new Vec2d(0, 5), new Vec2d(10, 5)), roadA.getId());
        edgeA.setSlopeOverrides(List.of(
            new RoadEdge.SlopeOverride(0, 5, 2.0f),
            new RoadEdge.SlopeOverride(5, 10, 4.0f)
        ));

        RoadNode bStart = network.createNode(new Vec2d(5, 5));
        RoadNode bEnd = network.createNode(new Vec2d(5, 10));
        network.createEdge(bStart.getId(), bEnd.getId(), List.of(
            new Vec2d(5, 5), new Vec2d(5, 10)), roadB.getId());

        builder.detectAndSplitIntersections(network);

        List<RoadEdge> roadASegments = network.getEdges().values().stream()
            .filter(edge -> roadA.getId().equals(edge.getRoadId()))
            .sorted((left, right) -> Double.compare(
                left.getCenterlinePoints().getFirst().x,
                right.getCenterlinePoints().getFirst().x))
            .collect(Collectors.toList());

        assertEquals(2, roadASegments.size());

        RoadEdge west = roadASegments.getFirst();
        assertEquals(1, west.getSlopeOverrides().size());
        assertEquals(0, west.getSlopeOverrides().getFirst().startDistance, 1e-6);
        assertEquals(5, west.getSlopeOverrides().getFirst().endDistance, 1e-6);
        assertEquals(2.0f, west.getSlopeOverrides().getFirst().maxSlope);

        RoadEdge east = roadASegments.get(1);
        assertEquals(1, east.getSlopeOverrides().size());
        assertEquals(0, east.getSlopeOverrides().getFirst().startDistance, 1e-6);
        assertEquals(5, east.getSlopeOverrides().getFirst().endDistance, 1e-6);
        assertEquals(4.0f, east.getSlopeOverrides().getFirst().maxSlope);
    }

    @Test
    void adoptShapeAssignsSharedSourceRoadId() {
        RoadNetwork network = new RoadNetwork();

        RoadNetworkBuilder.AdoptResult result = builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)), false), config);

        String adoptGroup = result.edges().getFirst().getSourceRoadId();
        assertNotNull(adoptGroup);
        for (RoadEdge edge : result.edges()) {
            assertEquals(adoptGroup, edge.getSourceRoadId());
        }
    }

    @Test
    void splitEdgeInheritsSourceRoadId() {
        RoadNetwork network = new RoadNetwork();

        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        RoadNetworkBuilder.AdoptResult crossed = builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false), config);

        String adoptGroup = crossed.edges().getFirst().getSourceRoadId();
        assertNotNull(adoptGroup);
        for (RoadEdge edge : crossed.edges()) {
            assertEquals(adoptGroup, edge.getSourceRoadId());
        }
    }

    @Test
    void sameAdoptGroupSkipsSelfCrossingFalsePositive() {
        RoadNetwork network = new RoadNetwork();

        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(10, 10), new Vec2d(10, 0), new Vec2d(0, 10)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);

        long nodesNearSelfCross = network.getNodes().values().stream()
            .filter(node -> RoadGeometryUtils.pointsNear(
                node.getPosition(), new Vec2d(5, 5), RoadNetworkBuilder.NODE_TOLERANCE))
            .count();
        assertEquals(1, nodesNearSelfCross);
    }

    @Test
    void sameAdoptGroupSkipsIntersectionWhenRoadIdsDiffer() {
        RoadNetwork network = new RoadNetwork();

        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(10, 10), new Vec2d(10, 0), new Vec2d(0, 10)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);

        String adoptGroup = network.getEdges().values().stream()
            .map(RoadEdge::getSourceRoadId)
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow();
        List<RoadEdge> siblings = network.getEdges().values().stream()
            .filter(edge -> adoptGroup.equals(edge.getSourceRoadId()))
            .toList();
        assertTrue(siblings.size() >= 2);

        RoadEdge segmentA = siblings.get(0);
        RoadEdge segmentB = siblings.get(1);
        Road reassignedRoad = network.createRoad("reassigned-road");
        segmentB.setRoadId(reassignedRoad.getId());
        network.assignEdgeToRoad(segmentB.getId(), reassignedRoad.getId());
        assertNotEquals(segmentA.getRoadId(), segmentB.getRoadId());

        int nodesBefore = network.getNodes().size();
        int edgesBefore = network.getEdges().size();

        IntersectionResult result = builder.detectAndSplitIntersections(network);

        assertEquals(IntersectionResult.COMPLETE, result);
        assertEquals(nodesBefore, network.getNodes().size());
        assertEquals(edgesBefore, network.getEdges().size());
    }

    @Test
    void differentAdoptGroupsStillIntersect() {
        RoadNetwork network = new RoadNetwork();

        RoadNetworkBuilder.AdoptResult first = builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        RoadNetworkBuilder.AdoptResult second = builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 5), new Vec2d(5, 10)), false), config);

        assertNotEquals(
            first.edges().getFirst().getSourceRoadId(),
            second.edges().getFirst().getSourceRoadId());
        assertEquals(3, network.getEdges().size());
        assertEquals(1, network.getJunctionCount());
    }

    @Test
    void detectAndSplitIntersectionsDoesNotExplodeOnDuplicateParallelEdges() {
        RoadNetwork network = new RoadNetwork();
        Road road1 = network.createRoad("road-a");
        Road road2 = network.createRoad("road-b");
        RoadNode start = network.createNode(new Vec2d(490, 452));
        RoadNode end = network.createNode(new Vec2d(890, 146));
        List<Vec2d> points = List.of(
            new Vec2d(490, 452), new Vec2d(490, 452), new Vec2d(890, 146));
        network.createEdge(start.getId(), end.getId(), points, road1.getId());
        network.createEdge(start.getId(), end.getId(), points, road2.getId());

        int edgesBefore = network.getEdges().size();
        IntersectionResult result = builder.detectAndSplitIntersections(network);
        assertEquals(IntersectionResult.COMPLETE, result);
        assertEquals(edgesBefore, network.getEdges().size());

        IntersectionProbeResult probe = builder.probeIntersectionCompleteness(network);
        assertEquals(edgesBefore, network.getEdges().size());
        assertFalse(probe.hasPendingWork());
    }

    @Test
    void probeIntersectionCompletenessDetectsPendingWithoutMutatingNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");

        RoadNode aStart = network.createNode(new Vec2d(0, 5));
        RoadNode aEnd = network.createNode(new Vec2d(10, 5));
        network.createEdge(aStart.getId(), aEnd.getId(), List.of(
            new Vec2d(0, 5), new Vec2d(10, 5)), roadA.getId());

        RoadNode bStart = network.createNode(new Vec2d(5, 5));
        RoadNode bEnd = network.createNode(new Vec2d(5, 10));
        network.createEdge(bStart.getId(), bEnd.getId(), List.of(
            new Vec2d(5, 5), new Vec2d(5, 10)), roadB.getId());

        int edgesBefore = network.getEdges().size();
        IntersectionProbeResult probe = builder.probeIntersectionCompleteness(network);

        assertEquals(edgesBefore, network.getEdges().size());
        assertTrue(probe.hasPendingWork());
        assertEquals(IntersectionResult.COMPLETE, probe.result());
    }

    private static RoadNode findNodeNear(RoadNetwork network, Vec2d position) {
        for (RoadNode node : network.getNodes().values()) {
            if (RoadGeometryUtils.pointsNear(node.getPosition(), position, RoadNetworkBuilder.NODE_TOLERANCE)) {
                return node;
            }
        }
        return null;
    }
}
