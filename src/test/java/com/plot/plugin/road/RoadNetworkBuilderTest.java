package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.RoadCrossingMaterializer;
import com.plot.plugin.road.crossing.RoadCrossingDetector;
import com.plot.plugin.road.crossing.RoadCrossingReconciler;
import com.plot.plugin.road.graph.RoadGraphEdits;
import com.plot.plugin.road.model.RoadTopologyRoadSplitter;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkBuilderTest {

    private final RoadNetworkBuilder builder = new RoadNetworkBuilder();
    private final RoadSystemConfig config = new RoadSystemConfig("road_system");

    @Test
    void adoptedRoadDefaultsToFitTerrain() {
        RoadNetwork network = new RoadNetwork();
        PolylineShape shape = new PolylineShape(List.of(
            new Vec2d(0, 0),
            new Vec2d(100, 0)
        ), false);

        builder.adoptShape(network, shape, config);

        Road road = network.getRoads().values().iterator().next();
        assertEquals(RoadVerticalMode.FIT_TERRAIN, road.getVerticalMode());
    }

    @Test
    void adoptClosedRectanglePromotesToLoopWithDefaultSeam() {
        RoadNetwork network = new RoadNetwork();
        Polygon rectangle = new Polygon(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)));

        builder.adoptShape(network, rectangle, config);
        RoadTopologyRoadSplitter.repairAfterAdopt(network);

        assertEquals(1, network.getRoads().size());
        Road road = network.getRoads().values().iterator().next();
        assertEquals(com.plot.plugin.road.model.RoadTopologyMode.LOOP, road.getTopologyMode());
        assertNotNull(road.getLoopSeam());
        assertTrue(com.plot.plugin.road.station.RoadStationing.isStationable(network, road));
    }

    @Test
    void adoptClosedPolygonCreatesLoopEdgeWithoutPreviewGap() {
        RoadNetwork network = new RoadNetwork();
        Polygon hexagon = new Polygon(List.of(
            new Vec2d(10, 0),
            new Vec2d(5, 8.66),
            new Vec2d(-5, 8.66),
            new Vec2d(-10, 0),
            new Vec2d(-5, -8.66),
            new Vec2d(5, -8.66)));

        RoadNetworkBuilder.AdoptResult adopted = builder.adoptShape(network, hexagon, config);

        assertEquals(1, adopted.edges().size());
        RoadEdge edge = adopted.edges().getFirst();
        assertEquals(edge.getStartNodeId(), edge.getEndNodeId());
        assertEquals(7, edge.getCenterlinePoints().size());
        assertTrue(RoadGeometryUtils.pointsNear(
            edge.getCenterlinePoints().getFirst(), edge.getCenterlinePoints().getLast(), 1e-6));
    }

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
    void adoptSkipsIntersectionSplit() {
        RoadNetwork network = new RoadNetwork();

        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 5), new Vec2d(5, 10)), false), config);

        assertEquals(2, network.getEdges().size());
        assertEquals(4, network.getNodes().size());
        assertEquals(0, network.getJunctionCount());
    }

    @Test
    void adoptDoesNotSnapToExistingNode() {
        RoadNetwork network = new RoadNetwork();

        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(10, 5), new Vec2d(20, 5)), false), config);

        long nodesNearSharedEndpoint = network.getNodes().values().stream()
            .filter(node -> RoadGeometryUtils.pointsNear(node.getPosition(), new Vec2d(10, 5), 1e-6))
            .count();
        assertEquals(2, nodesNearSharedEndpoint);
    }

    @Test
    void reconcileAndMaterializeCreatesTJunction() {
        RoadNetwork network = new RoadNetwork();

        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 5), new Vec2d(5, 10)), false), config);

        reconcileAndMaterialize(network);

        assertEquals(3, network.getEdges().size());
        assertEquals(4, network.getNodes().size());
        assertEquals(1, network.getJunctionCount());

        RoadNode junction = findNodeNear(network, new Vec2d(5, 5));
        assertNotNull(junction);
        assertEquals(3, junction.getDegree());
        assertEquals(RoadNetworkBuilder.JunctionType.T_JUNCTION, builder.classify(junction));
    }

    @Test
    void reconcileAndMaterializeCreatesCrossroad() {
        RoadNetwork network = new RoadNetwork();

        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false), config);

        reconcileAndMaterialize(network);

        assertEquals(4, network.getEdges().size());
        assertEquals(5, network.getNodes().size());
        assertEquals(1, network.getJunctionCount());

        RoadNode junction = findNodeNear(network, new Vec2d(5, 5));
        assertNotNull(junction);
        assertEquals(4, junction.getDegree());
        assertEquals(RoadNetworkBuilder.JunctionType.CROSSROAD, builder.classify(junction));
    }

    @Test
    void nearEndpointWithinToleranceDoesNotCreateDuplicateNode() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");

        RoadNode aStart = network.createNode(new Vec2d(0, 5));
        RoadNode aEnd = network.createNode(new Vec2d(10, 5));
        RoadNode bStart = network.createNode(new Vec2d(5, 5));
        RoadNode bEnd = network.createNode(new Vec2d(5, 10));

        network.createEdge(aStart.getId(), aEnd.getId(), List.of(
            new Vec2d(0, 5), new Vec2d(10, 5)), roadA.getId());
        network.createEdge(bStart.getId(), bEnd.getId(), List.of(
            new Vec2d(5, 5), new Vec2d(5, 10)), roadB.getId());

        reconcileAndMaterialize(network);

        assertEquals(3, network.getEdges().size());
        assertEquals(4, network.getNodes().size());

        long nodesNearJunction = network.getNodes().values().stream()
            .filter(node -> RoadGeometryUtils.pointsNear(node.getPosition(), new Vec2d(5, 5), RoadNetworkBuilder.NODE_TOLERANCE))
            .count();
        assertEquals(1, nodesNearJunction);
    }

    @Test
    void reconcileAndMaterializeCompletesForCascadeIntersections() {
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

        IntersectionResult result = reconcileAndMaterialize(network);

        assertEquals(IntersectionResult.COMPLETE, result);
        assertEquals(5, network.getEdges().size());
        assertEquals(2, network.getJunctionCount());

        Set<String> roadASegments = network.getRoad(roadA.getId()).getSegmentIds();
        assertEquals(3, roadASegments.size());
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
        RoadCrossingReconciler.reconcileCrossings(network);

        assertEquals(1, network.getCrossings().size());
        assertEquals(2, network.getEdges().size());

        Road roadB = network.getRoad(roadBId);
        assertNotNull(roadB);
        assertEquals(1, roadB.getSegmentIds().size());
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

        RoadCrossingReconciler.reconcileCrossings(network);
        RoadNetwork materialized = RoadCrossingMaterializer.materializeForSnapshot(network);

        List<RoadEdge> roadASegments = materialized.getEdges().values().stream()
            .filter(edge -> roadA.getId().equals(edge.getRoadId()))
            .sorted(Comparator.comparingDouble(left -> left.getCenterlinePoints().getFirst().x))
            .toList();

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

        reconcileAndMaterialize(network);

        long nodesNearSelfCross = network.getNodes().values().stream()
            .filter(node -> RoadGeometryUtils.pointsNear(
                node.getPosition(), new Vec2d(5, 5), RoadNetworkBuilder.NODE_TOLERANCE))
            .count();
        assertEquals(1, nodesNearSelfCross);
    }

    @Test
    void differentRoadIdsRegisterCrossingEvenWithSharedSourceRoadId() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("self-cross");
        String adoptGroup = UUID.randomUUID().toString();

        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 10));
        RoadNode n3 = network.createNode(new Vec2d(10, 0));
        RoadNode n4 = network.createNode(new Vec2d(0, 10));
        RoadEdge segmentA = network.createEdge(n1.getId(), n2.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(10, 10)), road.getId());
        RoadEdge segmentB = network.createEdge(n3.getId(), n4.getId(), List.of(
            new Vec2d(10, 0), new Vec2d(0, 10)), road.getId());
        segmentA.setSourceRoadId(adoptGroup);
        segmentB.setSourceRoadId(adoptGroup);

        Road reassignedRoad = network.createRoad("reassigned-road");
        segmentB.setRoadId(reassignedRoad.getId());
        network.assignEdgeToRoad(segmentB.getId(), reassignedRoad.getId());
        assertNotEquals(segmentA.getRoadId(), segmentB.getRoadId());

        RoadCrossingReconciler.reconcileCrossings(network);

        assertEquals(1, network.getCrossings().size());
        assertEquals(2, network.getEdges().size());
    }

    @Test
    void differentAdoptGroupsRemainIndependentUntilReconcile() {
        RoadNetwork network = new RoadNetwork();

        RoadNetworkBuilder.AdoptResult first = builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        RoadNetworkBuilder.AdoptResult second = builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 5), new Vec2d(5, 10)), false), config);

        assertNotEquals(
            first.edges().getFirst().getSourceRoadId(),
            second.edges().getFirst().getSourceRoadId());
        assertEquals(2, network.getEdges().size());
        assertEquals(0, network.getJunctionCount());

        reconcileAndMaterialize(network);
        assertEquals(3, network.getEdges().size());
        assertEquals(1, network.getJunctionCount());
    }

    @Test
    void reconcileDoesNotExplodeOnDuplicateParallelEdges() {
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
        IntersectionResult result = RoadCrossingReconciler.reconcileCrossings(network);
        assertEquals(IntersectionResult.COMPLETE, result);
        assertEquals(edgesBefore, network.getEdges().size());

        IntersectionProbeResult probe = RoadCrossingReconciler.probeRegistryCompleteness(network);
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
        assertEquals(1, RoadCrossingDetector.detectAll(network).size());
        IntersectionProbeResult probe = builder.probeIntersectionCompleteness(network);

        assertEquals(edgesBefore, network.getEdges().size());
        assertTrue(probe.hasPendingWork());
        assertEquals(IntersectionResult.COMPLETE, probe.result());
    }

    private static IntersectionResult reconcileAndMaterialize(RoadNetwork network) {
        RoadCrossingReconciler.reconcileCrossings(network);
        return RoadCrossingMaterializer.materializeInPlace(network);
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
