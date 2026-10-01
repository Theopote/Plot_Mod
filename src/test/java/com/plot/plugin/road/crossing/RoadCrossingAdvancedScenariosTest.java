package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.overlay.IntersectionOverlaySource;
import com.plot.plugin.road.station.RoadStationing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 复杂编辑场景：同一道路对多次相交、反向道路 station、独立设计保留等。
 */
class RoadCrossingAdvancedScenariosTest {

    private static final double EPS = 1e-3;

    @Test
    void sameRoadPairTwoCrossings_detectedAndRegisteredSeparately() {
        RoadNetwork network = buildDoubleCrossingNetwork();

        List<RoadCrossing> detected = RoadCrossingDetector.detectAll(network);
        assertEquals(2, detected.size());

        List<RoadCrossing> byX = detected.stream()
            .sorted(Comparator.comparing(c -> c.position().x))
            .toList();
        assertEquals(10.0, byX.get(0).position().x, EPS);
        assertEquals(20.0, byX.get(1).position().x, EPS);
        assertEquals(5.0, byX.get(0).position().y, EPS);
        assertEquals(5.0, byX.get(1).position().y, EPS);

        RoadCrossingReconciler.reconcileCrossings(network);
        assertEquals(2, network.getCrossings().size());

        List<RoadCrossing> registered = sortedByX(network);
        assertNotEquals(registered.get(0).id(), registered.get(1).id());
    }

    @Test
    void sameRoadPairTwoCrossings_matchExistingUsesPositionNotAmbiguous() {
        RoadNetwork network = buildDoubleCrossingNetwork();
        RoadCrossingReconciler.reconcileCrossings(network);

        List<RoadCrossing> registered = sortedByX(network);
        List<RoadCrossing> detected = RoadCrossingDetector.detectAll(network).stream()
            .sorted(Comparator.comparing(c -> c.position().x))
            .toList();

        Set<String> matched = new HashSet<>();
        RoadCrossing firstMatch = RoadCrossingMatcher.matchExisting(
            detected.get(0), registered, matched);
        assertNotNull(firstMatch);
        assertEquals(registered.get(0).id(), firstMatch.id());
        matched.add(firstMatch.id());

        RoadCrossing secondMatch = RoadCrossingMatcher.matchExisting(
            detected.get(1), registered, matched);
        assertNotNull(secondMatch);
        assertEquals(registered.get(1).id(), secondMatch.id());
    }

    @Test
    void sameRoadPairTwoCrossings_reconcilePreservesIndependentGradeSeparation() {
        RoadNetwork network = buildDoubleCrossingNetwork();
        RoadCrossingReconciler.reconcileCrossings(network);

        List<RoadCrossing> registered = sortedByX(network);
        RoadCrossing west = registered.get(0);
        RoadCrossing east = registered.get(1);
        String verticalRoadId = findVerticalRoadId(network, west);

        network.setCrossingGradeSeparation(
            west.id(), CrossingType.GRADE_SEPARATED, verticalRoadId, 4.0);
        network.setCrossingGradeSeparation(
            east.id(), CrossingType.AT_GRADE, null, null);

        RoadCrossingReconciler.reconcileCrossings(network);

        RoadCrossing preservedWest = network.getCrossing(west.id());
        RoadCrossing preservedEast = network.getCrossing(east.id());
        assertNotNull(preservedWest);
        assertNotNull(preservedEast);
        assertEquals(CrossingType.GRADE_SEPARATED, preservedWest.type());
        assertEquals(4.0, preservedWest.crossingClearance(), EPS);
        assertEquals(CrossingType.AT_GRADE, preservedEast.type());
    }

    @Test
    void removingOneGeometricCrossing_dropsOnlyMatchingRegistryEntry() {
        RoadNetwork network = buildDoubleCrossingNetwork();
        RoadCrossingReconciler.reconcileCrossings(network);

        RoadCrossing east = sortedByX(network).get(1);
        String eastId = east.id();

        Road roadB = network.getRoad("road-b");
        RoadEdge edgeB = network.getEdge(roadB.getOrderedSegmentIds().getFirst());
        edgeB.setCenterlinePoints(List.of(new Vec2d(10, 0), new Vec2d(10, 10)));

        RoadCrossingReconciler.reconcileCrossings(network);

        assertEquals(1, network.getCrossings().size());
        RoadCrossing remaining = network.getCrossings().values().iterator().next();
        assertNull(network.getCrossing(eastId));
        assertEquals(10.0, remaining.position().x, EPS);
    }

    @Test
    void reverseRoad_reconcilePreservesCrossingPhysicalChainage() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");
        network.createEdge(
            network.createNode(new Vec2d(0, 5)).getId(),
            network.createNode(new Vec2d(10, 5)).getId(),
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)),
            roadA.getId());
        network.createEdge(
            network.createNode(new Vec2d(1, 0)).getId(),
            network.createNode(new Vec2d(1, 10)).getId(),
            List.of(new Vec2d(1, 0), new Vec2d(1, 10)),
            roadB.getId());

        RoadCrossingReconciler.reconcileCrossings(network);
        RoadCrossing original = network.getCrossings().values().iterator().next();
        double stationOnABefore = original.stationOn(roadA.getId());
        assertEquals(1.0, stationOnABefore, 0.5);

        assertTrue(RoadCenterlineEditor.reverseRoad(network, roadA).isSuccess());
        RoadCrossingReconciler.reconcileCrossings(network);

        RoadCrossing updated = network.getCrossings().values().iterator().next();
        assertEquals(original.id(), updated.id());
        double stationOnAAfter = updated.stationOn(roadA.getId());
        assertEquals(stationOnABefore, stationOnAAfter, 0.5);
        assertEquals(1.0, updated.position().x, EPS);
        assertEquals(5.0, updated.position().y, EPS);
        assertFalse(RoadStationing.orientedSegments(network, roadA).getFirst().forward());
    }

    @Test
    void reverseEdge_preservesCrossingPhysicalChainage() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");
        RoadEdge edgeA = network.createEdge(
            network.createNode(new Vec2d(0, 5)).getId(),
            network.createNode(new Vec2d(10, 5)).getId(),
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)),
            roadA.getId());
        network.createEdge(
            network.createNode(new Vec2d(1, 0)).getId(),
            network.createNode(new Vec2d(1, 10)).getId(),
            List.of(new Vec2d(1, 0), new Vec2d(1, 10)),
            roadB.getId());

        RoadCrossingReconciler.reconcileCrossings(network);
        RoadCrossing original = network.getCrossings().values().iterator().next();
        double stationBefore = original.stationOn(roadA.getId());

        assertTrue(RoadCenterlineEditor.reverseEdge(network, edgeA.getId()).isSuccess());
        RoadCrossingReconciler.reconcileCrossings(network);

        RoadCrossing updated = network.getCrossings().values().iterator().next();
        assertEquals(original.id(), updated.id());
        assertEquals(stationBefore, updated.stationOn(roadA.getId()), 0.5);
        assertFalse(RoadStationing.orientedSegments(network, roadA).getFirst().forward());
    }

    @Test
    void reverseRoadViaManager_preservesCrossingIdentityAndPhysicalStation() {
        RoadNetworkManager manager = new RoadNetworkManager(
            new RoadSystemConfig("test"), new RoadProjectStatus());
        manager.adoptSelectedPaths(List.of(
            new PolylineShape(List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false),
            new PolylineShape(List.of(new Vec2d(1, 0), new Vec2d(1, 10)), false)));

        RoadNetwork network = manager.getNetwork();
        Road horizontal = network.getRoads().values().stream()
            .filter(road -> Math.abs(RoadStationing.canonicalLength(network, road) - 10.0) < 1.0)
            .filter(road -> {
                var pt = RoadStationing.pointAtStation(network, road, 0.0).orElse(null);
                return pt != null && Math.abs(pt.y - 5.0) < EPS;
            })
            .findFirst()
            .orElseThrow();

        RoadCrossing original = network.getCrossings().values().iterator().next();
        String originalId = original.id();
        double stationBefore = original.stationOn(horizontal.getId());

        assertTrue(manager.reverseRoad(horizontal).isSuccess());

        RoadCrossing updated = network.getCrossing(originalId);
        assertNotNull(updated);
        assertEquals(stationBefore, updated.stationOn(horizontal.getId()), 0.5);
        assertEquals(1.0, updated.position().x, EPS);
        assertEquals(5.0, updated.position().y, EPS);
    }

    @Test
    void allSelectionEntryPointsUseFocusIntersectionSemantics() {
        RoadNetworkManager manager = new RoadNetworkManager(
            new RoadSystemConfig("test"), new RoadProjectStatus());
        manager.adoptSelectedPaths(List.of(
            new PolylineShape(List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false),
            new PolylineShape(List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false)));

        RoadCrossing crossing = manager.getNetwork().getCrossings().values().iterator().next();
        manager.clearEdgeSelection();
        manager.clearNodeSelection();
        manager.clearCrossingSelection();

        manager.focusIntersection(IntersectionOverlaySource.CROSSING, crossing.id());

        assertEquals(crossing.id(), manager.getSelectedCrossingId());
        assertFalse(manager.getSelectedEdgeIds().isEmpty());
    }

    @Test
    void threeAdjacentCrossings_oneRemovedAfterShift_preservesRemainingDesign() {
        RoadNetwork network = buildTripleCrossingNetwork();
        RoadCrossingReconciler.reconcileCrossings(network);

        List<RoadCrossing> registered = sortedByX(network);
        assertEquals(3, registered.size());
        String westId = registered.get(0).id();
        String middleId = registered.get(1).id();
        String eastId = registered.get(2).id();
        String verticalRoadId = findVerticalRoadId(network, registered.get(0));

        network.setCrossingGradeSeparation(westId, CrossingType.GRADE_SEPARATED, verticalRoadId, 4.0);
        network.setCrossingGradeSeparation(middleId, CrossingType.AT_GRADE, null, null);
        network.setCrossingGradeSeparation(eastId, CrossingType.GRADE_SEPARATED, verticalRoadId, 6.0);

        Road roadA = network.getRoad("road-a");
        RoadEdge edgeA = network.getEdge(roadA.getOrderedSegmentIds().getFirst());
        edgeA.setCenterlinePoints(List.of(new Vec2d(0, 5.2), new Vec2d(30, 5.2)));

        Road roadB = network.getRoad("road-b");
        RoadEdge edgeB = network.getEdge(roadB.getOrderedSegmentIds().getFirst());
        edgeB.setCenterlinePoints(List.of(
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(20, 10),
            new Vec2d(20, 0)));

        RoadCrossingReconciler.reconcileCrossings(network);

        assertEquals(2, network.getCrossings().size());
        assertNull(network.getCrossing(middleId));

        RoadCrossing preservedWest = network.getCrossing(westId);
        RoadCrossing preservedEast = network.getCrossing(eastId);
        assertNotNull(preservedWest);
        assertNotNull(preservedEast);
        assertEquals(CrossingType.GRADE_SEPARATED, preservedWest.type());
        assertEquals(4.0, preservedWest.crossingClearance(), EPS);
        assertEquals(CrossingType.GRADE_SEPARATED, preservedEast.type());
        assertEquals(6.0, preservedEast.crossingClearance(), EPS);
        assertEquals(10.0, preservedWest.position().x, EPS);
        assertEquals(20.0, preservedEast.position().x, EPS);
        assertEquals(5.2, preservedWest.position().y, EPS);
    }

    private static RoadNetwork buildTripleCrossingNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");
        network.createEdge(
            network.createNode(new Vec2d(0, 5)).getId(),
            network.createNode(new Vec2d(30, 5)).getId(),
            List.of(new Vec2d(0, 5), new Vec2d(30, 5)),
            roadA.getId());
        network.createEdge(
            network.createNode(new Vec2d(10, 0)).getId(),
            network.createNode(new Vec2d(20, 0)).getId(),
            List.of(
                new Vec2d(10, 0),
                new Vec2d(10, 10),
                new Vec2d(15, 10),
                new Vec2d(15, 0),
                new Vec2d(20, 0),
                new Vec2d(20, 10),
                new Vec2d(20, 0)),
            roadB.getId());
        return network;
    }

    @Test
    void twoDistinctCrossingsWithinMatchingTolerance_detectBoth() {
        RoadNetwork network = buildCloseDoubleCrossingNetwork();

        List<RoadCrossing> detected = RoadCrossingDetector.detectAll(network);
        assertEquals(2, detected.size());

        List<RoadCrossing> byX = detected.stream()
            .sorted(Comparator.comparing(c -> c.position().x))
            .toList();
        assertEquals(49.825, byX.get(0).position().x, EPS);
        assertEquals(50.175, byX.get(1).position().x, EPS);
        assertEquals(5.0, byX.get(0).position().y, EPS);
        assertEquals(5.0, byX.get(1).position().y, EPS);
        assertEquals(0.35, byX.get(1).position().x - byX.get(0).position().x, EPS);

        RoadCrossingReconciler.reconcileCrossings(network);
        assertEquals(2, network.getCrossings().size());
    }

    @Test
    void twoNearbyCrossingsPreserveDistinctDesignAfterShift() {
        RoadNetwork network = buildCloseDoubleCrossingNetwork();
        RoadCrossingReconciler.reconcileCrossings(network);

        List<RoadCrossing> registered = sortedByX(network);
        String westId = registered.get(0).id();
        String eastId = registered.get(1).id();
        String verticalRoadId = findVerticalRoadId(network, registered.get(0));

        network.setCrossingGradeSeparation(westId, CrossingType.GRADE_SEPARATED, verticalRoadId, 4.0);
        network.setCrossingGradeSeparation(eastId, CrossingType.AT_GRADE, null, null);

        Road roadA = network.getRoad("road-a");
        RoadEdge edgeA = network.getEdge(roadA.getOrderedSegmentIds().getFirst());
        edgeA.setCenterlinePoints(List.of(new Vec2d(0, 5.2), new Vec2d(100, 5.2)));

        Road roadB = network.getRoad("road-b");
        RoadEdge edgeB = network.getEdge(roadB.getOrderedSegmentIds().getFirst());
        edgeB.setCenterlinePoints(List.of(
            new Vec2d(40, -5.2),
            new Vec2d(49.825, 5.2),
            new Vec2d(49.825, 15.2),
            new Vec2d(50.175, 15.2),
            new Vec2d(50.175, 5.2),
            new Vec2d(60, -5.2)));

        RoadCrossingReconciler.reconcileCrossings(network);

        assertEquals(2, network.getCrossings().size());
        RoadCrossing west = network.getCrossing(westId);
        RoadCrossing east = network.getCrossing(eastId);
        assertNotNull(west);
        assertNotNull(east);
        assertEquals(CrossingType.GRADE_SEPARATED, west.type());
        assertEquals(4.0, west.crossingClearance(), EPS);
        assertEquals(verticalRoadId, west.elevatedRoadId());
        assertEquals(CrossingType.AT_GRADE, east.type());
        assertEquals(49.825, west.position().x, EPS);
        assertEquals(50.175, east.position().x, EPS);
    }

    @Test
    void probeRegistryCompleteness_detectsStaleGeometryAfterMatchedShift() {
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
        RoadCrossingReconciler.reconcileCrossings(network);

        RoadEdge horizontalEdge = network.getEdge(roadA.getOrderedSegmentIds().getFirst());
        horizontalEdge.setCenterlinePoints(List.of(new Vec2d(0, 5.3), new Vec2d(10, 5.3)));

        assertTrue(RoadCrossingReconciler.probeRegistryCompleteness(network).hasPendingWork());
    }

    private static RoadNetwork buildCloseDoubleCrossingNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");
        network.createEdge(
            network.createNode(new Vec2d(0, 5)).getId(),
            network.createNode(new Vec2d(100, 5)).getId(),
            List.of(new Vec2d(0, 5), new Vec2d(100, 5)),
            roadA.getId());
        network.createEdge(
            network.createNode(new Vec2d(40, -5)).getId(),
            network.createNode(new Vec2d(60, -5)).getId(),
            List.of(
                new Vec2d(40, -5),
                new Vec2d(49.825, 5),
                new Vec2d(49.825, 15),
                new Vec2d(50.175, 15),
                new Vec2d(50.175, 5),
                new Vec2d(60, -5)),
            roadB.getId());
        return network;
    }

    private static RoadNetwork buildDoubleCrossingNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");
        network.createEdge(
            network.createNode(new Vec2d(0, 5)).getId(),
            network.createNode(new Vec2d(30, 5)).getId(),
            List.of(new Vec2d(0, 5), new Vec2d(30, 5)),
            roadA.getId());
        network.createEdge(
            network.createNode(new Vec2d(10, 0)).getId(),
            network.createNode(new Vec2d(20, 0)).getId(),
            List.of(new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(20, 10), new Vec2d(20, 0)),
            roadB.getId());
        return network;
    }

    private static List<RoadCrossing> sortedByX(RoadNetwork network) {
        List<RoadCrossing> crossings = new ArrayList<>(network.getCrossings().values());
        crossings.sort(Comparator.comparing(c -> c.position().x));
        return crossings;
    }

    private static String findVerticalRoadId(RoadNetwork network, RoadCrossing crossing) {
        Road roadA = network.getRoad("road-a");
        if (roadA != null && crossing.involvesRoad(roadA.getId())) {
            return crossing.otherRoadId(roadA.getId());
        }
        return crossing.roadBId();
    }
}
