package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.crossing.RoadCrossingReconciler;
import com.plot.plugin.road.model.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoadJunctionOverlayControllerTest {

    @Test
    void hitTestReturnsClosestIntersectionWithinRadius() {
        List<RoadJunctionOverlayEntry> entries = List.of(
            new RoadJunctionOverlayEntry(
                IntersectionOverlaySource.LEGACY_NODE,
                "node-a",
                new Vec2d(0, 0),
                RoadJunctionOverlayKind.AT_GRADE,
                false),
            new RoadJunctionOverlayEntry(
                IntersectionOverlaySource.CROSSING,
                "cross-b",
                new Vec2d(10, 0),
                RoadJunctionOverlayKind.GRADE_SEPARATED,
                false));

        IntersectionHit hitA = RoadJunctionOverlayController.hitTest(entries, 0.5, 0.0, 2.0);
        assertNotNull(hitA);
        assertEquals(IntersectionOverlaySource.LEGACY_NODE, hitA.source());
        assertEquals("node-a", hitA.id());

        IntersectionHit hitB = RoadJunctionOverlayController.hitTest(entries, 9.5, 0.0, 2.0);
        assertNotNull(hitB);
        assertEquals(IntersectionOverlaySource.CROSSING, hitB.source());
        assertEquals("cross-b", hitB.id());

        assertNull(RoadJunctionOverlayController.hitTest(entries, 5.0, 0.0, 1.0));
    }

    @Test
    void snapshotIncludesRegisteredCrossings() {
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
        assertNotNull(entries.getFirst().position());
    }
}
