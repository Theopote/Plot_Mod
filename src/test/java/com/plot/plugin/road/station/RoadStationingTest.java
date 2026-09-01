package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadSegmentOrdering;
import com.plot.plugin.road.model.RoadTopologyMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadStationingTest {

    @Test
    void accumulatesLengthAlongOrderedChain() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-a");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(30, 0));
        RoadEdge e1 = network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(30, 0)), road.getId());

        assertEquals(30.0, RoadStationing.canonicalLength(network, road), 1e-6);
        assertEquals(0.0, RoadStationing.segmentStartStation(network, road, e1.getId()), 1e-6);
        assertEquals(10.0, RoadStationing.segmentStartStation(network, road, network.getEdge(road.getOrderedSegmentIds().get(1)).getId()), 1e-6);
    }

    @Test
    void stationAndSegmentRoundTrip() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-a");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(30, 0));
        RoadEdge e1 = network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        RoadEdge e2 = network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(30, 0)), road.getId());

        Optional<RoadStation> atMid = RoadStationing.stationAt(network, road, e2.getId(), 5.0);
        assertTrue(atMid.isPresent());
        assertEquals(15.0, atMid.get().chainageMeters(), 1e-6);

        Optional<SegmentStation> resolved = RoadStationing.resolve(network, road, 15.0);
        assertTrue(resolved.isPresent());
        assertEquals(e2.getId(), resolved.get().segmentId());
        assertEquals(5.0, resolved.get().localDistance(), 1e-6);
    }

    @Test
    void topologicalOrderDefinesChainageAfterReorder() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-a");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(30, 0));
        RoadEdge e1 = network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        RoadEdge e2 = network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(30, 0)), road.getId());
        road.reorderSegments(List.of(e2.getId(), e1.getId()));

        RoadSegmentOrdering.applyTopologicalOrder(network, road);

        assertEquals(0.0, RoadStationing.segmentStartStation(network, road, e1.getId()), 1e-6);
        assertEquals(10.0, RoadStationing.segmentStartStation(network, road, e2.getId()), 1e-6);
    }

    @Test
    void loopRoadIsStationable() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("ring");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(10, 10));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());
        network.createEdge(n3.getId(), n1.getId(), List.of(new Vec2d(10, 10), new Vec2d(0, 0)), road.getId());

        assertTrue(RoadStationing.isStationable(network, road));
        assertEquals(10.0 + 10.0 + Math.hypot(10, 10), RoadStationing.canonicalLength(network, road), 1e-4);
    }

    @Test
    void branchingRoadIsNotStationable() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("fork");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        RoadNode d = network.createNode(new Vec2d(10, 10));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        network.createEdge(b.getId(), d.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());

        assertFalse(RoadStationing.isStationable(network, road));
        assertTrue(RoadStationing.resolve(network, road, 5.0).isEmpty());
    }

    @Test
    void formatFromEndUsesEPrefix() {
        assertEquals("EK0+000", RoadStationing.formatFromEnd(0.0, RoadStationFormat.KILOMETER_PLUS));
        assertEquals("EK0+020", RoadStationing.formatFromEnd(20.0, RoadStationFormat.KILOMETER_PLUS));
        assertEquals("EK0+100", RoadStationing.format(0.0, 100.0, RoadStationFormat.KILOMETER_PLUS, ChainageDisplayMode.FROM_END));
    }

    @Test
    void formatStationLabels() {
        assertEquals("K0+000", RoadStationing.format(0.0, RoadStationFormat.KILOMETER_PLUS));
        assertEquals("K0+020", RoadStationing.format(20.0, RoadStationFormat.KILOMETER_PLUS));
        assertEquals("K1+200", RoadStationing.format(1200.0, RoadStationFormat.KILOMETER_PLUS));
        assertEquals("0+020", RoadStationing.format(20.0, RoadStationFormat.PLAIN_PLUS));
    }

    @Test
    void endStationResolvesToLastSegmentEnd() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-a");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());

        double total = RoadStationing.canonicalLength(network, road);
        Optional<SegmentStation> end = RoadStationing.resolve(network, road, total);
        assertTrue(end.isPresent());
        assertEquals(10.0, end.get().localDistance(), 1e-6);
    }

    @Test
    void orientedSegmentsExposeForwardFlagsAlongChain() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-a");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(30, 0));
        RoadNode d = network.createNode(new Vec2d(30, 20));
        RoadEdge edge1 = network.createEdge(
            a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        RoadEdge edge2 = network.createEdge(
            c.getId(), b.getId(), List.of(new Vec2d(30, 0), new Vec2d(10, 0)), road.getId());
        RoadEdge edge3 = network.createEdge(
            c.getId(), d.getId(), List.of(new Vec2d(30, 0), new Vec2d(30, 20)), road.getId());

        List<OrientedRoadSegment> oriented = RoadStationing.orientedSegments(network, road);
        assertEquals(3, oriented.size());
        assertEquals(edge1.getId(), oriented.get(0).edgeId());
        assertTrue(oriented.get(0).forward());
        assertEquals(edge2.getId(), oriented.get(1).edgeId());
        assertFalse(oriented.get(1).forward());
        assertEquals(edge3.getId(), oriented.get(2).edgeId());
        assertTrue(oriented.get(2).forward());
        assertEquals(0.0, oriented.get(0).startStation(), 1e-6);
        assertEquals(10.0, oriented.get(1).startStation(), 1e-6);
        assertEquals(30.0, oriented.get(2).startStation(), 1e-6);
    }

    @Test
    void reversedSegmentGeometryUsesChainDirectionForStationLookup() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-a");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(30, 0));
        RoadEdge head = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        RoadEdge tail = network.createEdge(
            n3.getId(), n2.getId(), List.of(new Vec2d(30, 0), new Vec2d(10, 0)), road.getId());

        assertFalse(RoadStationing.segmentFlowsWithGeometry(network, road, tail.getId()));
        assertEquals(25.0, RoadStationing.stationAt(network, road, tail.getId(), 5.0).get().chainageMeters(), 1e-6);

        Optional<SegmentStation> resolved = RoadStationing.resolve(network, road, 15.0);
        assertTrue(resolved.isPresent());
        assertEquals(tail.getId(), resolved.get().segmentId());
        assertEquals(15.0, resolved.get().localDistance(), 1e-6);

        assertEquals(0.0, RoadStationing.segmentStartStation(network, road, head.getId()), 1e-6);
    }
}
