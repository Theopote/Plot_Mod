package com.plot.plugin.road.graph;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NearbyJunctionClusterAnalyzerTest {

    @Test
    void findsClusterWithinThreshold() {
        RoadNetwork network = new RoadNetwork();
        RoadNode crossA = createSimpleCross(network, new Vec2d(0, 0));
        RoadNode crossB = createSimpleCross(network, new Vec2d(2, 0));

        List<NearbyJunctionClusterAnalyzer.NearbyJunctionCluster> clusters =
            NearbyJunctionClusterAnalyzer.findClusters(network, 3.0);

        assertEquals(1, clusters.size());
        assertEquals(2, clusters.getFirst().nodeIds().size());
        assertTrue(clusters.getFirst().contains(crossA.getId()));
        assertTrue(clusters.getFirst().contains(crossB.getId()));
    }

    @Test
    void ignoresDistantJunctions() {
        RoadNetwork network = new RoadNetwork();
        createSimpleCross(network, new Vec2d(0, 0));
        createSimpleCross(network, new Vec2d(10, 0));

        assertTrue(NearbyJunctionClusterAnalyzer.findClusters(network, 3.0).isEmpty());
    }

    @Test
    void clusterContainingReturnsMatch() {
        RoadNetwork network = new RoadNetwork();
        RoadNode crossA = createSimpleCross(network, new Vec2d(0, 0));
        RoadNode crossB = createSimpleCross(network, new Vec2d(1, 0));

        NearbyJunctionClusterAnalyzer.NearbyJunctionCluster cluster =
            NearbyJunctionClusterAnalyzer.clusterContaining(network, crossA.getId(), 3.0);
        assertNotNull(cluster);
        assertTrue(cluster.contains(crossB.getId()));
    }

    private static RoadNode createSimpleCross(RoadNetwork network, Vec2d center) {
        RoadNode junction = network.createNode(center);
        RoadNode north = network.createNode(new Vec2d(center.x, center.y + 10));
        RoadNode south = network.createNode(new Vec2d(center.x, center.y - 10));
        RoadNode east = network.createNode(new Vec2d(center.x + 10, center.y));
        RoadNode west = network.createNode(new Vec2d(center.x - 10, center.y));
        Road roadA = network.createRoad("road-ns-" + junction.getId());
        Road roadB = network.createRoad("road-ew-" + junction.getId());
        network.createEdge(
            junction.getId(), north.getId(),
            List.of(center, north.getPosition()), roadA.getId());
        network.createEdge(
            junction.getId(), south.getId(),
            List.of(center, south.getPosition()), roadA.getId());
        network.createEdge(
            junction.getId(), east.getId(),
            List.of(center, east.getPosition()), roadB.getId());
        network.createEdge(
            junction.getId(), west.getId(),
            List.of(center, west.getPosition()), roadB.getId());
        return junction;
    }
}
