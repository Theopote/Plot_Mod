package com.plot.plugin.road.graph;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadGraphTest {

    @Test
    void connectedComponentsDetectsIsolatedNetworks() {
        RoadNetwork network = new RoadNetwork();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(100, 0));
        RoadNode d = network.createNode(new Vec2d(110, 0));
        network.createEdge(a.getId(), b.getId(), line(a.getPosition(), b.getPosition()));
        network.createEdge(c.getId(), d.getId(), line(c.getPosition(), d.getPosition()));

        List<RoadGraphQueries.GraphComponent> components = RoadGraphQueries.of(network).connectedComponents();

        assertEquals(2, components.size());
        assertEquals(2, components.get(0).nodeIds().size());
        assertEquals(1, components.get(0).edgeIds().size());
    }

    @Test
    void deadEndAndJunctionQueriesClassifyNodes() {
        RoadNetwork network = tJunctionNetwork();

        List<RoadNode> deadEnds = RoadGraphQueries.of(network).deadEndNodes();
        List<RoadNode> junctions = RoadGraphQueries.of(network).junctionNodes();
        List<RoadNode> through = RoadGraphQueries.of(network).throughNodes();

        assertEquals(3, deadEnds.size());
        assertEquals(1, junctions.size());
        assertEquals(0, through.size());
    }

    @Test
    void shortestPathUsesEdgeLength() {
        RoadNetwork network = new RoadNetwork();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(10, 10));
        network.createEdge(a.getId(), b.getId(), line(a.getPosition(), b.getPosition()));
        network.createEdge(b.getId(), c.getId(), line(b.getPosition(), c.getPosition()));

        RoadGraphQueries.GraphPath path = RoadGraphQueries.of(network)
            .shortestPath(a.getId(), c.getId())
            .orElseThrow();

        assertEquals(List.of(a.getId(), b.getId(), c.getId()), path.nodeIds());
        assertEquals(2, path.edgeIds().size());
        assertEquals(20.0, path.length(), 1e-6);
    }

    @Test
    void splitAndMergeRoundTripPreservesRoadMembership() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad();
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(20, 0));
        RoadNode mid = network.createNode(new Vec2d(10, 0));
        RoadEdge edge = network.createEdge(
            start.getId(),
            end.getId(),
            line(start.getPosition(), mid.getPosition(), end.getPosition()),
            road.getId()
        );

        RoadGraphEdits.SplitResult split = RoadGraphEdits.of(network)
            .splitEdgeAtNode(edge.getId(), mid.getId())
            .orElseThrow();

        assertEquals(2, network.getEdges().size());
        assertEquals(2, road.getSegmentIds().size());
        assertEquals(null, network.getEdge(edge.getId()));

        String mergedEdgeId = RoadGraphEdits.of(network)
            .mergeThroughNode(mid.getId())
            .orElseThrow();

        assertEquals(1, network.getEdges().size());
        assertEquals(1, road.getSegmentIds().size());
        assertEquals(mergedEdgeId, road.getSegmentIds().iterator().next());
        assertEquals(20.0, network.getEdge(mergedEdgeId).getLength(), 1e-6);
        assertEquals(null, network.getNode(mid.getId()));
    }

    @Test
    void mergeRejectsDifferentRoads() {
        RoadNetwork network = throughNodeNetwork();
        RoadNode through = network.getNodes().values().stream()
            .filter(node -> node.getDegree() == 2)
            .findFirst()
            .orElseThrow();

        assertTrue(RoadGraphEdits.of(network).mergeThroughNode(through.getId()).isEmpty());
    }

    @Test
    void findCycleDetectsRingRoad() {
        RoadNetwork network = ringNetwork();
        List<List<String>> cycles = RoadGraphQueries.of(network).findCycles();

        assertFalse(cycles.isEmpty());
        assertTrue(cycles.getFirst().size() >= 3);
    }

    private static RoadNetwork tJunctionNetwork() {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode left = network.createNode(new Vec2d(-10, 0));
        RoadNode right = network.createNode(new Vec2d(10, 0));
        RoadNode spur = network.createNode(new Vec2d(0, 10));
        network.createEdge(left.getId(), junction.getId(), line(left.getPosition(), junction.getPosition()));
        network.createEdge(junction.getId(), right.getId(), line(junction.getPosition(), right.getPosition()));
        network.createEdge(junction.getId(), spur.getId(), line(junction.getPosition(), spur.getPosition()));
        return network;
    }

    private static RoadNetwork throughNodeNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad();
        Road roadB = network.createRoad();
        RoadNode left = network.createNode(new Vec2d(0, 0));
        RoadNode through = network.createNode(new Vec2d(10, 0));
        RoadNode right = network.createNode(new Vec2d(20, 0));
        network.createEdge(left.getId(), through.getId(), line(left.getPosition(), through.getPosition()), roadA.getId());
        network.createEdge(through.getId(), right.getId(), line(through.getPosition(), right.getPosition()), roadB.getId());
        return network;
    }

    private static RoadNetwork ringNetwork() {
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(10, 10));
        RoadNode n4 = network.createNode(new Vec2d(0, 10));
        network.createEdge(n1.getId(), n2.getId(), line(n1.getPosition(), n2.getPosition()));
        network.createEdge(n2.getId(), n3.getId(), line(n2.getPosition(), n3.getPosition()));
        network.createEdge(n3.getId(), n4.getId(), line(n3.getPosition(), n4.getPosition()));
        network.createEdge(n4.getId(), n1.getId(), line(n4.getPosition(), n1.getPosition()));
        return network;
    }

    private static List<Vec2d> line(Vec2d... points) {
        return List.of(points);
    }
}
