package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadNetworkEngineeringValidator;
import com.plot.plugin.road.RoadNetworkValidationReport;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.alignment.TurnDirection;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadLoopSeam;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.station.OrientedRoadSegment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2.1 geometry / topology regression coverage (G04–G07, G10, G12–G15) plus finalization.
 */
class Phase21CrossingGeometryTest {

    @Test
    void loopSeamAtTeeDoesNotDropCrossing() {
        // G04
        RoadNetwork network = squareLoopWithBranchAt(new Vec2d(5, 0));
        Road loop = network.getRoad("loop");
        RoadEdge bottom = network.getEdges().values().stream()
            .filter(e -> loop.getId().equals(e.getRoadId())
                && e.getCenterlinePoints().getFirst().y == 0
                && e.getCenterlinePoints().getLast().y == 0)
            .findFirst()
            .orElseThrow();
        loop.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(5, 0), bottom.getId(), 0.5));

        List<RoadCrossing> crossings = RoadCrossingDetector.detectAll(network);
        assertEquals(1, crossings.size(), "T-junction at LOOP seam must remain detectable");
        RoadCrossing crossing = crossings.getFirst();
        Set<String> roads = Set.of(crossing.roadAId(), crossing.roadBId());
        assertTrue(roads.contains(loop.getId()));
        assertTrue(roads.contains(network.getRoad("branch").getId()));
    }

    @Test
    void movingLoopSeamKeepsCrossingIdentity() {
        // G06 — count and participating roads stay stable
        RoadNetwork network = squareLoopWithBranchAt(new Vec2d(5, 0));
        Road loop = network.getRoad("loop");
        List<RoadEdge> loopEdges = network.getEdges().values().stream()
            .filter(e -> loop.getId().equals(e.getRoadId()))
            .toList();

        loop.setLoopSeam(RoadLoopSeam.at(new Vec2d(0, 0)));
        Set<String> roadsA = crossingRoadPairs(RoadCrossingDetector.detectAll(network));

        loop.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(5, 0), loopEdges.getFirst().getId(), 0.5));
        Set<String> roadsB = crossingRoadPairs(RoadCrossingDetector.detectAll(network));

        loop.setLoopSeam(RoadLoopSeam.at(new Vec2d(10, 10)));
        Set<String> roadsC = crossingRoadPairs(RoadCrossingDetector.detectAll(network));

        assertEquals(1, roadsA.size());
        assertEquals(roadsA, roadsB);
        assertEquals(roadsA, roadsC);
    }

    @Test
    void rectangularLoopHasZeroSelfIntersections() {
        RoadNetwork network = squareLoopOnly();
        assertEquals(0, RoadStackCrossingDetector.detectAll(network).size());
    }

    @Test
    void circularLoopHasZeroSelfIntersections() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("circle");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        List<Vec2d> ring = new ArrayList<>();
        int segments = 16;
        double radius = 20.0;
        for (int i = 0; i < segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            ring.add(new Vec2d(radius * Math.cos(angle), radius * Math.sin(angle)));
        }
        RoadNode first = network.createNode(ring.getFirst().copy());
        RoadNode prev = first;
        for (int i = 1; i < segments; i++) {
            RoadNode next = network.createNode(ring.get(i).copy());
            network.createEdge(
                prev.getId(),
                next.getId(),
                List.of(ring.get(i - 1).copy(), ring.get(i).copy()),
                road.getId());
            prev = next;
        }
        network.createEdge(
            prev.getId(),
            first.getId(),
            List.of(ring.getLast().copy(), ring.getFirst().copy()),
            road.getId());
        road.setLoopSeam(RoadLoopSeam.at(ring.getFirst()));

        assertEquals(0, RoadStackCrossingDetector.detectAll(network).size());
    }

    @Test
    void figureEightSelfIntersectionAtSeamIsKeptExactlyOnce() {
        // G05
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("eight");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        network.createEdge(
            n1.getId(),
            n2.getId(),
            List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 10),
                new Vec2d(0, 10),
                new Vec2d(10, 0)),
            road.getId());
        network.createEdge(
            n2.getId(),
            n1.getId(),
            List.of(new Vec2d(10, 0), new Vec2d(0, 0)),
            road.getId());
        road.setLoopSeam(RoadLoopSeam.at(new Vec2d(5, 5)));

        List<RoadStackCrossing> stacks = RoadStackCrossingDetector.detectAll(network);
        assertEquals(1, stacks.size(), "exactly one real self-crossing must remain");
        assertTrue(stacks.getFirst().position().distance(new Vec2d(5, 5)) < 0.5);
    }

    @Test
    void multiPassSamePlanPositionKeepsExactStationPairs() {
        // G07 — three station passes through (0,0) along one polyline
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("helix");
        RoadNode n1 = network.createNode(new Vec2d(-10, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        network.createEdge(
            n1.getId(),
            n2.getId(),
            List.of(
                new Vec2d(-10, 0),
                new Vec2d(0, 0),
                new Vec2d(0, 10),
                new Vec2d(0, 0),
                new Vec2d(0, -10),
                new Vec2d(0, 0),
                new Vec2d(10, 0)),
            road.getId());

        List<RoadStackCrossing> stacks = RoadStackCrossingDetector.detectForRoad(network, road);
        assertEquals(3, stacks.size(), "exactly three station pairs at the shared plan point");
        Set<String> keys = new HashSet<>();
        Set<String> stationPairs = new HashSet<>();
        for (RoadStackCrossing stack : stacks) {
            keys.add(stack.stableKey());
            stationPairs.add(
                Math.round(stack.stationA() * 100.0) + "|" + Math.round(stack.stationB() * 100.0));
            assertTrue(stack.position().distance(new Vec2d(0, 0)) < 0.5);
        }
        assertEquals(3, keys.size());
        assertEquals(3, stationPairs.size());
        assertFalse(RoadStackCrossingDetector.multiLayerWarnings(stacks).isEmpty());

        for (RoadStackCrossing stack : stacks) {
            network.registerStackCrossing(stack);
        }
        RoadNetworkValidationReport report =
            RoadNetworkEngineeringValidator.analyzeOverviewHealth(network, false);
        assertTrue(report.items().stream().anyMatch(
            item -> "plugin.road.validation.multi_layer_stack".equals(item.messageKey())));
    }

    @Test
    void discontinuousSegmentsDoNotInventCrossingAcrossGap() {
        // G10 — two partial polylines assigned to one road with a gap; third road in the gap
        RoadNetwork network = new RoadNetwork();
        Road broken = network.createRoad("broken");
        RoadNode a1 = network.createNode(new Vec2d(0, 0));
        RoadNode a2 = network.createNode(new Vec2d(4, 0));
        RoadNode b1 = network.createNode(new Vec2d(6, 0));
        RoadNode b2 = network.createNode(new Vec2d(10, 0));
        network.createEdge(a1.getId(), a2.getId(), List.of(new Vec2d(0, 0), new Vec2d(4, 0)), broken.getId());
        network.createEdge(b1.getId(), b2.getId(), List.of(new Vec2d(6, 0), new Vec2d(10, 0)), broken.getId());

        Road cross = network.createRoad("cross");
        RoadNode c1 = network.createNode(new Vec2d(5, -5));
        RoadNode c2 = network.createNode(new Vec2d(5, 5));
        network.createEdge(c1.getId(), c2.getId(), List.of(new Vec2d(5, -5), new Vec2d(5, 5)), cross.getId());

        assertTrue(RoadCrossingDetector.detectAll(network).isEmpty(),
            "gap must not create a phantom chord that crosses the third road");
    }

    @Test
    void topologicallyConnectedButGeometricallyGappedCenterlineIsRejected() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("gapped");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode mid = network.createNode(new Vec2d(5, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        // Topology mid is shared, but centerlines leave a 2m geometric gap around mid.
        network.createEdge(a.getId(), mid.getId(), List.of(new Vec2d(0, 0), new Vec2d(4, 0)), road.getId());
        network.createEdge(mid.getId(), b.getId(), List.of(new Vec2d(6, 0), new Vec2d(10, 0)), road.getId());

        Road cross = network.createRoad("cross");
        RoadNode c1 = network.createNode(new Vec2d(5, -5));
        RoadNode c2 = network.createNode(new Vec2d(5, 5));
        network.createEdge(c1.getId(), c2.getId(), List.of(new Vec2d(5, -5), new Vec2d(5, 5)), cross.getId());

        assertTrue(RoadCrossingDetector.detectAll(network).isEmpty(),
            "appendJoined must reject discontinuous centerline even when nodes are linked");
        assertTrue(RoadStackCrossingDetector.detectForRoad(network, road).isEmpty());
    }

    @Test
    void inventoryDoesNotHideUnrelatedCrossingAtSamePlanPosition() {
        // G12
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("a");
        Road roadB = network.createRoad("b");
        Road roadC = network.createRoad("c");
        Road roadD = network.createRoad("d");

        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadNode south = network.createNode(new Vec2d(0, -10));
        RoadNode east = network.createNode(new Vec2d(10, 0));
        RoadNode west = network.createNode(new Vec2d(-10, 0));
        network.createEdge(junction.getId(), north.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, 10)), roadA.getId());
        network.createEdge(junction.getId(), south.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, -10)), roadA.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)), roadB.getId());
        network.createEdge(junction.getId(), west.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(-10, 0)), roadB.getId());

        network.registerCrossing(RoadCrossing.atGrade(
            roadC.getId(), 1.0, roadD.getId(), 1.0, new Vec2d(0, 0)));

        assertEquals(1, RoadIntersectionInventory.listLegacyJunctionNodes(network).size());
        assertEquals(2, RoadIntersectionInventory.count(network));
    }

    @Test
    void inventoryDoesNotHideAbNodeWhenOnlyAcCrossingSharesPosition() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("a");
        Road roadB = network.createRoad("b");
        Road roadC = network.createRoad("c");

        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadNode south = network.createNode(new Vec2d(0, -10));
        RoadNode east = network.createNode(new Vec2d(10, 0));
        RoadNode west = network.createNode(new Vec2d(-10, 0));
        network.createEdge(junction.getId(), north.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, 10)), roadA.getId());
        network.createEdge(junction.getId(), south.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, -10)), roadB.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)), roadA.getId());
        network.createEdge(junction.getId(), west.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(-10, 0)), roadB.getId());

        // Registry covers A-C only; A-B junction at same plan position must remain listed.
        network.registerCrossing(RoadCrossing.atGrade(
            roadA.getId(), 5.0, roadC.getId(), 5.0, new Vec2d(0, 0)));

        assertEquals(1, RoadIntersectionInventory.listLegacyJunctionNodes(network).size());
    }

    @Test
    void hundredDisjointRoadsProduceNoCrossings() {
        // G13
        RoadNetwork network = new RoadNetwork();
        for (int i = 0; i < 100; i++) {
            Road road = network.createRoad("r" + i);
            double y = i * 20.0;
            RoadNode n1 = network.createNode(new Vec2d(0, y));
            RoadNode n2 = network.createNode(new Vec2d(10, y));
            network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, y), new Vec2d(10, y)), road.getId());
        }
        assertTrue(RoadCrossingDetector.detectAll(network).isEmpty());
    }

    @Test
    void fiveHundredRoadStressDetectionCompletes() {
        RoadNetwork network = new RoadNetwork();
        for (int i = 0; i < 250; i++) {
            Road road = network.createRoad("h" + i);
            double y = i * 4.0;
            RoadNode n1 = network.createNode(new Vec2d(0, y));
            RoadNode n2 = network.createNode(new Vec2d(1000, y));
            network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, y), new Vec2d(1000, y)), road.getId());
        }
        for (int i = 0; i < 250; i++) {
            Road road = network.createRoad("v" + i);
            double x = i * 4.0 + 2.0;
            RoadNode n1 = network.createNode(new Vec2d(x, -5));
            RoadNode n2 = network.createNode(new Vec2d(x, 1005));
            network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(x, -5), new Vec2d(x, 1005)), road.getId());
        }

        long start = System.nanoTime();
        List<RoadCrossing> crossings = RoadCrossingDetector.detectAll(network);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertEquals(250 * 250, crossings.size());
        assertTrue(elapsedMs < 30_000L, "500-road grid detection took " + elapsedMs + "ms");
    }

    @Test
    void mixedNetworkDetectionIsStableAcrossRepeatedRuns() {
        // G14 / G15 light: result identity stable
        RoadNetwork network = new RoadNetwork();
        for (int i = 0; i < 20; i++) {
            Road road = network.createRoad("h" + i);
            double y = i * 5.0;
            RoadNode n1 = network.createNode(new Vec2d(0, y));
            RoadNode n2 = network.createNode(new Vec2d(50, y));
            network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, y), new Vec2d(50, y)), road.getId());
        }
        for (int i = 0; i < 20; i++) {
            Road road = network.createRoad("v" + i);
            double x = i * 5.0 + 2.5;
            RoadNode n1 = network.createNode(new Vec2d(x, -5));
            RoadNode n2 = network.createNode(new Vec2d(x, 100));
            network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(x, -5), new Vec2d(x, 100)), road.getId());
        }

        List<RoadCrossing> first = RoadCrossingDetector.detectAll(network);
        List<RoadCrossing> second = RoadCrossingDetector.detectAll(network);
        assertEquals(first.size(), second.size());
        assertTrue(first.size() > 50);
        assertEquals(crossingKeys(first), crossingKeys(second));
    }

    @Test
    void stackCrossingRoundTripPreservesDesignFields() throws Exception {
        RoadNetwork network = figureEightLinear();
        RoadStackCrossingReconciler.reconcileStackCrossings(network);
        assertEquals(1, network.getStackCrossings().size());
        RoadStackCrossing original = network.getStackCrossings().values().iterator().next();
        RoadStackCrossing designed = new RoadStackCrossing(
            original.id(),
            original.roadId(),
            original.position(),
            original.stationA(),
            original.stationB(),
            CrossingType.GRADE_SEPARATED,
            original.stationA(),
            original.stationB(),
            6.0,
            null);
        network.registerStackCrossing(designed);

        RoadNetwork loaded = RoadNetwork.fromJson(network.toJson());
        assertEquals(1, loaded.getStackCrossings().size());
        RoadStackCrossing restored = loaded.getStackCrossings().values().iterator().next();
        assertEquals(CrossingType.GRADE_SEPARATED, restored.type());
        assertEquals(6.0, restored.crossingClearance(), 1e-6);
        assertEquals(designed.stableKey(), restored.stableKey());
    }

    @Test
    void haSampleStationsInterpolateWithoutChordFallback() {
        List<com.plot.plugin.road.alignment.PlanCenterlineSample> samples = List.of(
            new com.plot.plugin.road.alignment.PlanCenterlineSample(new Vec2d(0, 0), 0.0),
            new com.plot.plugin.road.alignment.PlanCenterlineSample(new Vec2d(10, 0), 12.5),
            new com.plot.plugin.road.alignment.PlanCenterlineSample(new Vec2d(20, 0), 25.0));
        var mid = RoadCrossingDetectionSupport.interpolateHaStation(samples, 0, new Vec2d(5, 0));
        assertTrue(mid.isPresent());
        assertEquals(6.25, mid.getAsDouble(), 1e-6);
    }

    @Test
    void haRoadCrossingUsesCanonicalStations() {
        RoadNetwork network = new RoadNetwork();
        Road haRoad = network.createRoad("ha");
        haRoad.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0),
            0.0,
            List.of(HorizontalAlignmentElement.tangent(100.0))));
        RoadNode a0 = network.createNode(new Vec2d(0, 0));
        RoadNode a1 = network.createNode(new Vec2d(100, 0));
        network.createEdge(a0.getId(), a1.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), haRoad.getId());

        Road cross = network.createRoad("cross");
        RoadNode c0 = network.createNode(new Vec2d(50, -20));
        RoadNode c1 = network.createNode(new Vec2d(50, 20));
        network.createEdge(c0.getId(), c1.getId(), List.of(new Vec2d(50, -20), new Vec2d(50, 20)), cross.getId());

        List<RoadCrossing> crossings = RoadCrossingDetector.detectAll(network);
        assertEquals(1, crossings.size());
        RoadCrossing crossing = crossings.getFirst();
        double haStation = haRoad.getId().equals(crossing.roadAId())
            ? crossing.stationA()
            : crossing.stationB();
        assertEquals(50.0, haStation, 1.0);
        assertTrue(haStation >= 0.0 && haStation <= 100.0 + 1e-3);
    }

    @Test
    void haCurvePathCrossingUsesInterpolatedSampleStations() {
        RoadNetwork network = new RoadNetwork();
        Road haRoad = network.createRoad("curve");
        haRoad.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0),
            0.0,
            List.of(
                HorizontalAlignmentElement.tangent(30.0),
                HorizontalAlignmentElement.circularArc(31.41592653589793, 20.0, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(30.0))));
        // Instance polyline roughly follows the HA corridor for detector fallback geometry.
        List<Vec2d> approx = List.of(
            new Vec2d(0, 0),
            new Vec2d(30, 0),
            new Vec2d(50, 20),
            new Vec2d(30, 40),
            new Vec2d(0, 40));
        RoadNode a0 = network.createNode(approx.getFirst().copy());
        RoadNode a1 = network.createNode(approx.getLast().copy());
        network.createEdge(a0.getId(), a1.getId(), approx, haRoad.getId());

        // Cross near the arc mid — should resolve to a finite canonical station inside HA length.
        Road cross = network.createRoad("cross");
        RoadNode c0 = network.createNode(new Vec2d(50, 0));
        RoadNode c1 = network.createNode(new Vec2d(50, 40));
        network.createEdge(c0.getId(), c1.getId(), List.of(new Vec2d(50, 0), new Vec2d(50, 40)), cross.getId());

        List<RoadCrossing> crossings = RoadCrossingDetector.detectAll(network);
        assertFalse(crossings.isEmpty());
        RoadCrossing crossing = crossings.getFirst();
        double haStation = haRoad.getId().equals(crossing.roadAId())
            ? crossing.stationA()
            : crossing.stationB();
        double haLength = 30.0 + 31.41592653589793 + 30.0;
        assertTrue(haStation > 20.0 && haStation < haLength - 5.0);
    }

    @Test
    void loopHaStationsUseSampleCanonicalDomainWithoutSecondSeamRotation() {
        RoadNetwork network = squareLoopOnly();
        Road loop = network.getRoad("loop");
        loop.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0),
            0.0,
            List.of(HorizontalAlignmentElement.tangent(40.0))));
        RoadEdge bottom = network.getEdges().values().stream()
            .filter(e -> loop.getId().equals(e.getRoadId())
                && e.getCenterlinePoints().getFirst().y == 0
                && e.getCenterlinePoints().getLast().y == 0)
            .findFirst()
            .orElseThrow();
        loop.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(5, 0), bottom.getId(), 0.5));

        RoadCrossingDetectionSupport.DetectionSession session =
            new RoadCrossingDetectionSupport.DetectionSession(network);
        RoadCrossingDetectionSupport.CachedRoadGeometry geometry = session.geometryFor(loop);
        assertTrue(geometry.valid());
        assertEquals(
            RoadCrossingDetectionSupport.StationSource.HA_CANONICAL,
            geometry.stationSource());
        assertTrue(geometry.samples().size() >= 2);

        int segmentIndex = Math.min(1, geometry.samples().size() - 2);
        Vec2d start = geometry.samples().get(segmentIndex).position();
        Vec2d end = geometry.samples().get(segmentIndex + 1).position();
        Vec2d hit = new Vec2d((start.x + end.x) * 0.5, (start.y + end.y) * 0.5);
        double expected = RoadCrossingDetectionSupport.interpolateHaStation(
            geometry.samples(), segmentIndex, hit).orElseThrow();

        // detectionChainage deliberately nonsense — HA path must ignore it / seam re-rotation.
        var actual = session.toCanonicalChainage(loop, geometry, segmentIndex, hit, 9999.0);
        assertTrue(actual.isPresent());
        assertEquals(expected, actual.getAsDouble(), 1e-6);
        assertTrue(actual.getAsDouble() >= 0.0 && actual.getAsDouble() <= 40.0 + 1e-3);
    }

    @Test
    void detectionSessionCachesPolylineShapeInstance() {
        RoadNetwork network = figureEightLinear();
        Road road = network.getRoad("figure-eight");
        RoadCrossingDetectionSupport.DetectionSession session =
            new RoadCrossingDetectionSupport.DetectionSession(network);
        RoadCrossingDetectionSupport.CachedRoadGeometry geometry = session.geometryFor(road);
        assertTrue(geometry.valid());
        assertTrue(geometry.polyline() == geometry.polyline());
        assertTrue(session.geometryFor(road).polyline() == geometry.polyline());
    }

    @Test
    void emptyAndZeroLengthRoadsAreNotStationableForDetection() {
        RoadNetwork network = new RoadNetwork();
        Road empty = network.createRoad("empty");
        assertTrue(RoadStackCrossingDetector.detectForRoad(network, empty).isEmpty());

        Road zero = network.createRoad("zero");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(0, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 0)), zero.getId());
        assertTrue(RoadStackCrossingDetector.detectForRoad(network, zero).isEmpty());
    }

    @Test
    void exactClipUsedForPartialOrientedSlice() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(2, 0));
        List<Vec2d> centerline = List.of(
            new Vec2d(0, 0),
            new Vec2d(1, 0),
            new Vec2d(1, 1),
            new Vec2d(2, 1));
        RoadEdge edge = network.createEdge(n1.getId(), n2.getId(), centerline, road.getId());
        OrientedRoadSegment real = new OrientedRoadSegment(
            edge.getId(), true, n1.getId(), n2.getId(), 0.0, 2.5, 0.5, 3.0);
        assertTrue(real.isPartialSlice());
        List<Vec2d> clipped = RoadCrossingDetectionSupport.orientedSegmentPoints(network, real);
        assertFalse(clipped.isEmpty());
        assertTrue(clipped.getFirst().distance(new Vec2d(0.5, 0)) < 1e-6);
        assertTrue(clipped.stream().anyMatch(p -> p.distance(new Vec2d(1, 0)) < 1e-6));
        assertTrue(clipped.stream().anyMatch(p -> p.distance(new Vec2d(1, 1)) < 1e-6));
        assertTrue(clipped.getLast().distance(new Vec2d(2, 1)) < 1e-6);
    }

    @Test
    void ambiguousSamePositionStackMatcherDoesNotSwapDesign() {
        RoadStackCrossing detected = RoadStackCrossing.atGrade(
            "road", 10.0, 30.0, new Vec2d(0, 0));
        RoadStackCrossing keep = RoadStackCrossing.gradeSeparated(
            "road", 10.0, 30.0, new Vec2d(0, 0), 4.0);
        RoadStackCrossing other = RoadStackCrossing.gradeSeparated(
            "road", 10.0, 50.0, new Vec2d(0, 0), 8.0);
        // Far from both station pairs, same plan position — must not pick arbitrarily.
        RoadStackCrossing drifted = RoadStackCrossing.atGrade(
            "road", 20.0, 40.0, new Vec2d(0, 0));

        RoadStackCrossing matched = RoadStackCrossingMatcher.matchExisting(
            drifted, List.of(keep, other), Set.of());
        assertEquals(null, matched, "ambiguous co-located stacks must not inherit design silently");

        RoadStackCrossing exact = RoadStackCrossingMatcher.matchExisting(
            detected, List.of(keep, other), Set.of());
        assertEquals(keep.id(), exact.id());
        assertEquals(4.0, exact.crossingClearance(), 1e-6);
    }

    private static Set<String> crossingKeys(List<RoadCrossing> crossings) {
        Set<String> keys = new HashSet<>();
        for (RoadCrossing crossing : crossings) {
            keys.add(crossing.stableKey());
        }
        return keys;
    }

    private static Set<String> crossingRoadPairs(List<RoadCrossing> crossings) {
        Set<String> pairs = new HashSet<>();
        for (RoadCrossing crossing : crossings) {
            pairs.add(RoadCrossingMatcher.roadPairKey(crossing.roadAId(), crossing.roadBId()));
        }
        return pairs;
    }

    private static RoadNetwork squareLoopOnly() {
        RoadNetwork network = new RoadNetwork();
        Road loop = network.createRoad("loop");
        loop.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode n0 = network.createNode(new Vec2d(0, 0));
        RoadNode n1 = network.createNode(new Vec2d(10, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 10));
        RoadNode n3 = network.createNode(new Vec2d(0, 10));
        network.createEdge(n0.getId(), n1.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), loop.getId());
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), loop.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 10), new Vec2d(0, 10)), loop.getId());
        network.createEdge(n3.getId(), n0.getId(), List.of(new Vec2d(0, 10), new Vec2d(0, 0)), loop.getId());
        loop.setLoopSeam(RoadLoopSeam.at(new Vec2d(0, 0)));
        return network;
    }

    private static RoadNetwork squareLoopWithBranchAt(Vec2d tee) {
        RoadNetwork network = squareLoopOnly();
        Road branch = network.createRoad("branch");
        RoadNode b0 = network.createNode(tee.copy());
        RoadNode b1 = network.createNode(new Vec2d(tee.x, tee.y - 10));
        network.createEdge(b0.getId(), b1.getId(), List.of(tee.copy(), new Vec2d(tee.x, tee.y - 10)), branch.getId());
        return network;
    }

    private static RoadNetwork figureEightLinear() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("figure-eight");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        network.createEdge(
            n1.getId(),
            n2.getId(),
            List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 10),
                new Vec2d(0, 10),
                new Vec2d(10, 0)),
            road.getId());
        return network;
    }
}
