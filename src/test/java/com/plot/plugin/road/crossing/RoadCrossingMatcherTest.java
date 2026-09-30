package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCrossingMatcherTest {

    @Test
    void detectorFindsCrossingOnExtendedHorizontalRoad() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");
        RoadNode a1 = network.createNode(new Vec2d(-5, 5));
        RoadNode a2 = network.createNode(new Vec2d(10, 5));
        network.createEdge(
            a1.getId(), a2.getId(), List.of(new Vec2d(-5, 5), new Vec2d(10, 5)), roadA.getId());
        RoadNode b1 = network.createNode(new Vec2d(5, 0));
        RoadNode b2 = network.createNode(new Vec2d(5, 10));
        network.createEdge(
            b1.getId(), b2.getId(), List.of(new Vec2d(5, 0), new Vec2d(5, 10)), roadB.getId());

        assertEquals(1, RoadCrossingDetector.detectAll(network).size());
    }

    @Test
    void reconcilePreservesGradeSeparationAfterRoadStationShift() {
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
        String verticalRoadId = Math.abs(original.stationA() - 5.0) < Math.abs(original.stationB() - 5.0)
            ? original.roadAId() : original.roadBId();

        network.setCrossingGradeSeparation(
            originalId,
            CrossingType.GRADE_SEPARATED,
            verticalRoadId,
            6.0);

        Road horizontalRoad = network.getRoads().values().stream()
            .filter(road -> !road.getId().equals(verticalRoadId))
            .findFirst()
            .orElseThrow();
        RoadEdge horizontalEdge = network.getEdge(horizontalRoad.getOrderedSegmentIds().getFirst());
        RoadNode startNode = network.getNode(horizontalEdge.getStartNodeId());
        startNode.setPosition(new Vec2d(-5, 5));
        horizontalEdge.setCenterlinePoints(List.of(new Vec2d(-5, 5), new Vec2d(10, 5)));

        List<RoadCrossing> detected = RoadCrossingDetector.detectAll(network);
        assertEquals(1, detected.size(), "expected one detected crossing after geometry shift");
        RoadCrossing registered = network.getCrossing(originalId);
        RoadCrossing matched = RoadCrossingMatcher.matchExisting(
            detected.getFirst(), List.of(registered), Set.of());
        assertNotNull(matched, () -> "detected=" + detected.getFirst() + " registered=" + registered);

        RoadCrossingReconciler.reconcileCrossings(network);

        assertEquals(1, network.getCrossings().size());
        RoadCrossing preserved = network.getCrossings().values().iterator().next();
        assertEquals(originalId, preserved.id());
        assertEquals(CrossingType.GRADE_SEPARATED, preserved.type());
        assertEquals(verticalRoadId, preserved.elevatedRoadId());
        assertEquals(6.0, preserved.crossingClearance(), 1e-6);
        assertEquals(5.0, preserved.position().x, 1e-6);
        assertEquals(5.0, preserved.position().y, 1e-6);
        double horizontalStationBefore = original.involvesRoad(horizontalRoad.getId())
            ? original.stationOn(horizontalRoad.getId()) : -1;
        double horizontalStationAfter = preserved.involvesRoad(horizontalRoad.getId())
            ? preserved.stationOn(horizontalRoad.getId()) : -1;
        assertNotEquals(horizontalStationBefore, horizontalStationAfter, 1e-6);
    }

    @Test
    void detectsCrossingFromPlanGeometryWhenStoredCenterlineDiffers() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");
        RoadNode a1 = network.createNode(new Vec2d(0, 5));
        RoadNode a2 = network.createNode(new Vec2d(10, 5));
        network.createEdge(
            a1.getId(), a2.getId(), List.of(new Vec2d(0, 5), new Vec2d(10, 5)), roadA.getId());

        RoadNode b1 = network.createNode(new Vec2d(8, 0));
        RoadNode b2 = network.createNode(new Vec2d(8, 10));
        network.createEdge(
            b1.getId(), b2.getId(), List.of(new Vec2d(8, 0), new Vec2d(8, 10)), roadB.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(
            new Vec2d(5, 0), Math.PI / 2, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(10.0));
        roadB.setHorizontalAlignment(alignment);

        List<RoadCrossing> detected = RoadCrossingDetector.detectAll(network);
        assertEquals(1, detected.size());
        assertEquals(5.0, detected.getFirst().position().x, 1e-3);
        assertEquals(5.0, detected.getFirst().position().y, 1e-3);
    }

    @Test
    void reconcileDetailedReportsNoChangeWhenRegistryAlreadyComplete() {
        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        RoadSystemConfig config = new RoadSystemConfig("crossing");
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false), config);

        CrossingReconcileResult first = RoadCrossingReconciler.reconcileCrossingsDetailed(network);
        assertTrue(first.changed());
        assertEquals(1, first.added());

        CrossingReconcileResult second = RoadCrossingReconciler.reconcileCrossingsDetailed(network);
        assertFalse(second.changed());
        assertEquals(0, second.added());
        assertEquals(0, second.removed());
        assertEquals(0, second.updated());
    }

    @Test
    void matchExistingUsesPositionWhenStableKeyDrifts() {
        RoadCrossing existing = new RoadCrossing(
            "cross-1",
            "road-a",
            50.0,
            "road-b",
            80.0,
            new Vec2d(5, 5),
            CrossingType.GRADE_SEPARATED,
            "road-a",
            6.0,
            null);
        RoadCrossing detected = RoadCrossing.atGrade("road-a", 55.0, "road-b", 80.0, new Vec2d(5, 5));

        RoadCrossing matched = RoadCrossingMatcher.matchExisting(
            detected, List.of(existing), Set.of());
        assertNotNull(matched);
        assertEquals("cross-1", matched.id());
    }
}
