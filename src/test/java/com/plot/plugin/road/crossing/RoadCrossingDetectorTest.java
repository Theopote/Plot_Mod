package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RoadCrossingDetectorTest {

    @Test
    void detectCrossing_registersRoadCrossing_notSharedNode() {
        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        RoadSystemConfig config = new RoadSystemConfig("crossing");

        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false), config);

        int nodesBefore = network.getNodes().size();
        RoadCrossingReconciler.reconcileCrossings(network);

        assertEquals(1, network.getCrossings().size());
        assertEquals(nodesBefore, network.getNodes().size());
        assertEquals(2, network.getEdges().size());
        assertFalse(network.getJunctionCount() > 0);
        assertEquals(1, RoadIntersectionInventory.count(network));
    }

    @Test
    void detectsCrossingWhenVerticalStartsOnHorizontalInterior() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");
        RoadNode aStart = network.createNode(new Vec2d(0, 5));
        RoadNode aEnd = network.createNode(new Vec2d(10, 5));
        network.createEdge(
            aStart.getId(), aEnd.getId(), List.of(new Vec2d(0, 5), new Vec2d(10, 5)), roadA.getId());
        RoadNode bStart = network.createNode(new Vec2d(5, 5));
        RoadNode bEnd = network.createNode(new Vec2d(5, 10));
        network.createEdge(
            bStart.getId(), bEnd.getId(), List.of(new Vec2d(5, 5), new Vec2d(5, 10)), roadB.getId());

        assertEquals(1, RoadCrossingDetector.detectAll(network).size());
    }

    @Test
    void reconcilePreservesCrossingDesignIntent() {
        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        RoadSystemConfig config = new RoadSystemConfig("crossing");

        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false), config);

        RoadCrossingReconciler.reconcileCrossings(network);
        RoadCrossing original = network.getCrossings().values().iterator().next();
        String originalId = original.id();
        String verticalRoadId = original.roadAId();
        String horizontalRoadId = original.roadBId();
        if (Math.abs(original.stationA() - 5.0) > Math.abs(original.stationB() - 5.0)) {
            verticalRoadId = original.roadBId();
            horizontalRoadId = original.roadAId();
        }

        network.setCrossingGradeSeparation(
            originalId,
            CrossingType.GRADE_SEPARATED,
            verticalRoadId,
            6.0);

        RoadCrossingReconciler.reconcileCrossings(network);

        RoadCrossing preserved = network.getCrossings().values().iterator().next();
        assertEquals(originalId, preserved.id());
        assertEquals(CrossingType.GRADE_SEPARATED, preserved.type());
        assertEquals(verticalRoadId, preserved.elevatedRoadId());
        assertEquals(6.0, preserved.crossingClearance(), 1e-6);
        assertNotEquals(horizontalRoadId, preserved.elevatedRoadId());
    }

    @Test
    void detectsCrossingAfterHorizontalVerticalShift() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");
        network.createEdge(
            network.createNode(new Vec2d(0, 5.3)).getId(),
            network.createNode(new Vec2d(10, 5.3)).getId(),
            List.of(new Vec2d(0, 5.3), new Vec2d(10, 5.3)),
            roadA.getId());
        network.createEdge(
            network.createNode(new Vec2d(5, 0)).getId(),
            network.createNode(new Vec2d(5, 10)).getId(),
            List.of(new Vec2d(5, 0), new Vec2d(5, 10)),
            roadB.getId());

        assertEquals(1, RoadCrossingDetector.detectAll(network).size());
    }

    @Test
    void detectCrossingWithStackedProfilesRegistersGradeSeparated() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");
        network.createEdge(
            network.createNode(new Vec2d(0, 5)).getId(),
            network.createNode(new Vec2d(10, 5)).getId(),
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)),
            roadA.getId());
        network.createEdge(
            network.createNode(new Vec2d(5, 0)).getId(),
            network.createNode(new Vec2d(5, 10)).getId(),
            List.of(new Vec2d(5, 0), new Vec2d(5, 10)),
            roadB.getId());
        roadA.setVerticalAlignment(new com.plot.plugin.road.vertical.RoadVerticalAlignment(List.of(
            com.plot.plugin.road.vertical.PointOfVerticalIntersection.of(0.0, 64.0),
            com.plot.plugin.road.vertical.PointOfVerticalIntersection.of(10.0, 64.0))));
        roadB.setVerticalAlignment(new com.plot.plugin.road.vertical.RoadVerticalAlignment(List.of(
            com.plot.plugin.road.vertical.PointOfVerticalIntersection.of(0.0, 72.0),
            com.plot.plugin.road.vertical.PointOfVerticalIntersection.of(10.0, 72.0))));

        RoadCrossingReconciler.reconcileCrossings(network);

        assertEquals(1, network.getCrossings().size());
        RoadCrossing crossing = network.getCrossings().values().iterator().next();
        assertEquals(CrossingType.GRADE_SEPARATED, crossing.type());
        assertEquals(roadB.getId(), crossing.elevatedRoadId());
    }

    @Test
    void reconcileKeepsUserAtGradeWhenProfilesLaterDiverge() {
        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        RoadSystemConfig config = new RoadSystemConfig("crossing");
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false), config);
        RoadCrossingReconciler.reconcileCrossings(network);
        RoadCrossing original = network.getCrossings().values().iterator().next();
        assertEquals(CrossingType.AT_GRADE, original.type());

        network.getRoad(original.roadAId()).setVerticalAlignment(
            new com.plot.plugin.road.vertical.RoadVerticalAlignment(List.of(
                com.plot.plugin.road.vertical.PointOfVerticalIntersection.of(0.0, 64.0),
                com.plot.plugin.road.vertical.PointOfVerticalIntersection.of(10.0, 64.0))));
        network.getRoad(original.roadBId()).setVerticalAlignment(
            new com.plot.plugin.road.vertical.RoadVerticalAlignment(List.of(
                com.plot.plugin.road.vertical.PointOfVerticalIntersection.of(0.0, 80.0),
                com.plot.plugin.road.vertical.PointOfVerticalIntersection.of(10.0, 80.0))));
        RoadCrossingReconciler.reconcileCrossings(network);

        RoadCrossing preserved = network.getCrossings().values().iterator().next();
        assertEquals(original.id(), preserved.id());
        assertEquals(CrossingType.AT_GRADE, preserved.type());
    }

    @Test
    void doesNotDetectCrossingWhenEndpointIsNearButNotOnOtherRoad() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");
        network.createEdge(
            network.createNode(new Vec2d(0, 5)).getId(),
            network.createNode(new Vec2d(10, 5)).getId(),
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)),
            roadA.getId());
        network.createEdge(
            network.createNode(new Vec2d(5, 5.3)).getId(),
            network.createNode(new Vec2d(5, 10)).getId(),
            List.of(new Vec2d(5, 5.3), new Vec2d(5, 10)),
            roadB.getId());

        assertEquals(0, RoadCrossingDetector.detectAll(network).size());
    }
}
