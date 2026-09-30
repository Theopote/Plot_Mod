package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.RoadCrossingReconciler;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.overlay.IntersectionHit;
import com.plot.plugin.road.overlay.IntersectionOverlaySource;
import com.plot.plugin.road.overlay.RoadJunctionOverlayController;
import com.plot.plugin.road.overlay.RoadJunctionOverlayEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkOverviewRendererTest {

    @Test
    void hitTestEdgeSelectsNearestSegment() {
        RoadNetwork network = new RoadNetwork();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadEdge horizontal = network.createEdge(
            a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)));

        String hit = RoadNetworkOverviewRenderer.hitTestEdge(network, 5, 0.5, 2.0);
        assertEquals(horizontal.getId(), hit);
    }

    @Test
    void hitTestEdgeReturnsNullWhenTooFar() {
        RoadNetwork network = new RoadNetwork();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)));

        assertNull(RoadNetworkOverviewRenderer.hitTestEdge(network, 5, 50, 1.0));
    }

    @Test
    void mapHeightForWidthFollowsDefaultCanvasAspectRatio() {
        float width = 400f;
        float height = RoadNetworkOverviewRenderer.mapHeightForWidth(width);
        assertEquals(300f, height, 0.01f);
    }

    @Test
    void mapHeightForWidthEnforcesMinimum() {
        assertTrue(RoadNetworkOverviewRenderer.mapHeightForWidth(40f) >= 80f);
    }

    @Test
    void intersectionSnapshotIncludesRegisteredCrossings() {
        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        RoadSystemConfig config = new RoadSystemConfig("test");
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false), config);
        RoadCrossingReconciler.reconcileCrossings(network);

        List<RoadJunctionOverlayEntry> entries = RoadJunctionOverlayController.snapshot(
            network, builder, "", "");
        assertEquals(1, entries.size());
        assertEquals(IntersectionOverlaySource.CROSSING, entries.getFirst().source());

        Vec2d position = entries.getFirst().position();
        IntersectionHit hit = RoadJunctionOverlayController.hitTest(
            entries, position.x, position.y, 1.0);
        assertNotNull(hit);
        assertEquals(IntersectionOverlaySource.CROSSING, hit.source());
    }

    @Test
    void thumbnailMatchesBuildingFootprintSize() {
        assertEquals(104f, RoadNetworkOverviewRenderer.thumbnailWidth(), 0.01f);
        assertEquals(68f, RoadNetworkOverviewRenderer.thumbnailHeight(), 0.01f);
    }
}
