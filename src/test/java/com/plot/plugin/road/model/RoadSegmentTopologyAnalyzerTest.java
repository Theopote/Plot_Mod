package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadSegmentTopologyAnalyzerTest {

    @Test
    void classifiesSimpleChain() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-a");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());

        assertEquals(RoadSegmentTopologyKind.SIMPLE_CHAIN, RoadSegmentTopologyAnalyzer.classify(network, road));
        assertEquals(0, RoadSegmentTopologyAnalyzer.countNonSimpleChainRoads(network));
    }

    @Test
    void classifiesLoop() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-loop");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(10, 10));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());
        network.createEdge(n3.getId(), n1.getId(), List.of(new Vec2d(10, 10), new Vec2d(0, 0)), road.getId());

        assertEquals(RoadSegmentTopologyKind.LOOP, RoadSegmentTopologyAnalyzer.classify(network, road));
        assertEquals(1, RoadSegmentTopologyAnalyzer.countNonSimpleChainRoads(network));
    }

    @Test
    void classifiesFork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-fork");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        RoadNode d = network.createNode(new Vec2d(10, 10));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        network.createEdge(b.getId(), d.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());

        assertEquals(RoadSegmentTopologyKind.FORK, RoadSegmentTopologyAnalyzer.classify(network, road));
    }

    @Test
    void classifiesDisconnectedComponentsWithinSameRoad() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-split");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        RoadNode n4 = network.createNode(new Vec2d(110, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n3.getId(), n4.getId(), List.of(new Vec2d(100, 0), new Vec2d(110, 0)), road.getId());

        assertEquals(RoadSegmentTopologyKind.DISCONNECTED, RoadSegmentTopologyAnalyzer.classify(network, road));
    }
}
