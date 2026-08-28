package com.plot.plugin.road.spatial;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.BoundingBox;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadEdgeSpatialIndexTest {

    @Test
    void candidatePairsMatchBruteForceBoundingBoxFilter() {
        RoadNetwork network = buildGridNetwork(6, 6, 10.0);
        List<RoadEdge> edges = new ArrayList<>(network.getEdges().values());

        Set<String> bruteForce = bruteForcePairs(edges, 0.5);
        RoadEdgeSpatialIndex index = RoadEdgeSpatialIndex.build(edges, 0.5);
        Set<String> indexed = toPairKeys(index.candidatePairs());

        assertEquals(bruteForce, indexed);
    }

    @Test
    void sparseNetworkProducesFewerCandidatesThanQuadratic() {
        RoadNetwork network = buildGridNetwork(20, 20, 50.0);
        List<RoadEdge> edges = new ArrayList<>(network.getEdges().values());

        RoadEdgeSpatialIndex index = RoadEdgeSpatialIndex.build(edges, 0.5);
        int pairCount = index.candidatePairs().size();
        int quadratic = edges.size() * (edges.size() - 1) / 2;

        assertTrue(pairCount < quadratic / 4,
            () -> "expected spatial filtering to cut candidate pairs, got " + pairCount + " of " + quadratic);
    }

    @Test
    void expandedBoundsIncludeNearbyParallelEdges() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");
        RoadNode aStart = network.createNode(new Vec2d(0, 0));
        RoadNode aEnd = network.createNode(new Vec2d(10, 0));
        RoadNode bStart = network.createNode(new Vec2d(0, 0.4));
        RoadNode bEnd = network.createNode(new Vec2d(10, 0.4));
        network.createEdge(aStart.getId(), aEnd.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(10, 0)), roadA.getId());
        network.createEdge(bStart.getId(), bEnd.getId(), List.of(
            new Vec2d(0, 0.4), new Vec2d(10, 0.4)), roadB.getId());

        RoadEdgeSpatialIndex index = RoadEdgeSpatialIndex.build(network.getEdges().values(), 0.5);
        assertEquals(1, index.candidatePairs().size());
    }

    private static RoadNetwork buildGridNetwork(int columns, int rows, double spacing) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("grid-road");
        List<List<RoadNode>> nodes = new ArrayList<>(rows);
        for (int row = 0; row < rows; row++) {
            List<RoadNode> rowNodes = new ArrayList<>(columns);
            for (int col = 0; col < columns; col++) {
                rowNodes.add(network.createNode(new Vec2d(col * spacing, row * spacing)));
            }
            nodes.add(rowNodes);
        }

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns - 1; col++) {
                RoadNode start = nodes.get(row).get(col);
                RoadNode end = nodes.get(row).get(col + 1);
                network.createEdge(start.getId(), end.getId(), List.of(
                    start.getPosition(), end.getPosition()), road.getId());
            }
        }
        for (int col = 0; col < columns; col++) {
            for (int row = 0; row < rows - 1; row++) {
                RoadNode start = nodes.get(row).get(col);
                RoadNode end = nodes.get(row + 1).get(col);
                network.createEdge(start.getId(), end.getId(), List.of(
                    start.getPosition(), end.getPosition()), road.getId());
            }
        }
        return network;
    }

    private static Set<String> bruteForcePairs(List<RoadEdge> edges, double expansionTolerance) {
        Set<String> pairs = new HashSet<>();
        for (int i = 0; i < edges.size(); i++) {
            RoadEdge edgeA = edges.get(i);
            BoundingBox boxA = boundsOf(edgeA, expansionTolerance);
            for (int j = i + 1; j < edges.size(); j++) {
                RoadEdge edgeB = edges.get(j);
                BoundingBox boxB = boundsOf(edgeB, expansionTolerance);
                if (boxA != null && boxB != null && boxA.intersects(boxB)) {
                    String first = edgeA.getId().compareTo(edgeB.getId()) < 0 ? edgeA.getId() : edgeB.getId();
                    String second = edgeA.getId().compareTo(edgeB.getId()) < 0 ? edgeB.getId() : edgeA.getId();
                    pairs.add(first + '\0' + second);
                }
            }
        }
        return pairs;
    }

    private static BoundingBox boundsOf(RoadEdge edge, double expansionTolerance) {
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.isEmpty()) {
            return null;
        }
        if (points.size() == 1) {
            Vec2d point = points.getFirst();
            return new BoundingBox(
                point.x - expansionTolerance,
                point.y - expansionTolerance,
                point.x + expansionTolerance,
                point.y + expansionTolerance);
        }
        return BoundingBox.fromPoints(points.toArray(Vec2d[]::new)).expand(expansionTolerance);
    }

    private static Set<String> toPairKeys(List<RoadEdgeSpatialIndex.CandidatePair> pairs) {
        Set<String> keys = new HashSet<>();
        for (RoadEdgeSpatialIndex.CandidatePair pair : pairs) {
            keys.add(pair.edgeIdA() + '\0' + pair.edgeIdB());
        }
        return keys;
    }
}
