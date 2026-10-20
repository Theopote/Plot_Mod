package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoadStationingReadCacheTest {
    @Test
    void snapshotCachePreservesTraversalAndDoesNotCacheLiveEdits() {
        var live = chain();
        var snapshot = live.snapshot();
        Road road = snapshot.getRoad("main");
        var original = RoadStationing.orientedSegments(snapshot, road);
        boolean stationable = RoadStationing.isStationable(snapshot, road);
        var cache = new RoadStationingReadCache(snapshot);
        try (var ignored = cache.activate()) {
            assertEquals(original, RoadStationing.orientedSegments(snapshot, road));
            assertEquals(stationable, RoadStationing.isStationable(snapshot, road));
            var end = live.getNodes().values().stream().filter(n -> n.getPosition().x == 20).findFirst().orElseThrow();
            var next = live.createNode(new Vec2d(30, 0));
            live.createEdge(end.getId(), next.getId(), List.of(end.getPosition(), next.getPosition()), "main");
            assertEquals(3, RoadStationing.orientedSegments(live, live.getRoad("main")).size());
            assertEquals(2, RoadStationing.orientedSegments(snapshot, road).size());
        }
        assertNull(RoadStationingReadCache.activeFor(snapshot));
        assertEquals(3, RoadStationing.orientedSegments(live, live.getRoad("main")).size());
    }

    @Test
    void nestedScopesRestoreTheOuterCacheAndExceptionsReleaseIt() {
        var first = chain();
        var second = chain();
        var outer = new RoadStationingReadCache(first);
        var inner = new RoadStationingReadCache(second);
        assertThrows(IllegalStateException.class, () -> {
            try (var ignored = outer.activate()) {
                try (var nested = inner.activate()) {
                    assertNull(RoadStationingReadCache.activeFor(first));
                    assertSame(inner, RoadStationingReadCache.activeFor(second));
                }
                assertSame(outer, RoadStationingReadCache.activeFor(first));
                throw new IllegalStateException("cancel generation");
            }
        });
        assertNull(RoadStationingReadCache.activeFor(first));
        assertNull(RoadStationingReadCache.activeFor(second));
    }

    @Test
    void loopSeamTraversalAndPhysicalTraversalStayDistinct() {
        var network = chain();
        var road = network.getRoad("main");
        var end = network.getNodes().values().stream().filter(n -> n.getPosition().x == 20).findFirst().orElseThrow();
        var start = network.getNodes().values().stream().filter(n -> n.getPosition().x == 0).findFirst().orElseThrow();
        network.createEdge(end.getId(), start.getId(), List.of(end.getPosition(), new Vec2d(10, 10), start.getPosition()), "main");
        road.setTopologyMode(com.plot.plugin.road.model.RoadTopologyMode.LOOP);
        String seamEdge = road.getSegmentIds().iterator().next();
        road.setLoopSeam(com.plot.plugin.road.model.RoadLoopSeam.onSegment(new Vec2d(5, 0), seamEdge, 0.5));
        var traversal = RoadStationing.orientedSegments(network, road);
        var physical = RoadStationing.physicalEdgeSegmentsForMaterialization(network, road);
        var cache = new RoadStationingReadCache(network);
        try (var ignored = cache.activate()) {
            assertEquals(traversal, RoadStationing.orientedSegments(network, road));
            assertEquals(physical, RoadStationing.physicalEdgeSegmentsForMaterialization(network, road));
            assertEquals(4, RoadStationing.orientedSegments(network, road).size());
            assertEquals(3, RoadStationing.physicalEdgeSegmentsForMaterialization(network, road).size());
        }
    }

    private static RoadNetwork chain() {
        var network = new RoadNetwork();
        network.createRoad("main");
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(10, 0));
        var c = network.createNode(new Vec2d(20, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), "main");
        network.createEdge(b.getId(), c.getId(), List.of(b.getPosition(), c.getPosition()), "main");
        return network;
    }
}
