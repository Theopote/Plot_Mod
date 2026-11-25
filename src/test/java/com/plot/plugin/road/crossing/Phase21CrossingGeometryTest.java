package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadLoopSeam;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.station.OrientedRoadSegment;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2.1 geometry / topology regression coverage (G04–G07, G10, G12–G15).
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
    }

    @Test
    void movingLoopSeamKeepsCrossingCount() {
        // G06
        RoadNetwork network = squareLoopWithBranchAt(new Vec2d(5, 0));
        Road loop = network.getRoad("loop");
        List<RoadEdge> loopEdges = network.getEdges().values().stream()
            .filter(e -> loop.getId().equals(e.getRoadId()))
            .toList();

        loop.setLoopSeam(RoadLoopSeam.at(new Vec2d(0, 0)));
        int countA = RoadCrossingDetector.detectAll(network).size();

        loop.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(5, 0), loopEdges.getFirst().getId(), 0.5));
        int countB = RoadCrossingDetector.detectAll(network).size();

        loop.setLoopSeam(RoadLoopSeam.at(new Vec2d(10, 10)));
        int countC = RoadCrossingDetector.detectAll(network).size();

        assertEquals(1, countA);
        assertEquals(countA, countB);
        assertEquals(countA, countC);
    }

    @Test
    void figureEightSelfIntersectionAtSeamIsKept() {
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
        assertFalse(stacks.isEmpty(), "self-intersection at seam must not be filtered as an open endpoint");
    }

    @Test
    void multiPassSamePlanPositionKeepsDistinctStackPairs() {
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
        assertTrue(stacks.size() >= 2, "multiple station pairs at same XZ must not collapse to one");
        Set<String> keys = new HashSet<>();
        for (RoadStackCrossing stack : stacks) {
            keys.add(stack.stableKey());
        }
        assertEquals(stacks.size(), keys.size());
        assertFalse(RoadStackCrossingDetector.multiLayerWarnings(stacks).isEmpty());
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

        // Unrelated registry crossing at same XZ but different roads — must not cover the junction
        network.registerCrossing(RoadCrossing.atGrade(
            roadC.getId(), 1.0, roadD.getId(), 1.0, new Vec2d(0, 0)));

        assertEquals(1, RoadIntersectionInventory.listLegacyJunctionNodes(network).size());
        assertEquals(2, RoadIntersectionInventory.count(network));
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

        Set<String> keysFirst = new HashSet<>();
        for (RoadCrossing crossing : first) {
            keysFirst.add(crossing.stableKey());
        }
        Set<String> keysSecond = new HashSet<>();
        for (RoadCrossing crossing : second) {
            keysSecond.add(crossing.stableKey());
        }
        assertEquals(keysFirst, keysSecond);
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

    private static RoadNetwork squareLoopWithBranchAt(Vec2d tee) {
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

        Road branch = network.createRoad("branch");
        RoadNode b0 = network.createNode(tee.copy());
        RoadNode b1 = network.createNode(new Vec2d(tee.x, tee.y - 10));
        network.createEdge(b0.getId(), b1.getId(), List.of(tee.copy(), new Vec2d(tee.x, tee.y - 10)), branch.getId());
        return network;
    }
}
