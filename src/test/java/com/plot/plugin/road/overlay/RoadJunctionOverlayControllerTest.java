package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.crossing.RoadCrossingReconciler;
import com.plot.plugin.road.crossing.RoadStackCrossingReconciler;
import com.plot.plugin.road.model.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void filletPreviewArcsAppearWithoutSelectionWhenEnabled() {
        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        RoadSystemConfig config = new RoadSystemConfig("fillet-preview-unselected");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setDefaultCornerRadius(6.0f);

        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false), config);
        RoadCrossingReconciler.reconcileCrossings(network);

        List<RoadJunctionOverlayEntry> hidden = RoadJunctionOverlayController.snapshot(
            network, builder, config, null, "", null, false);
        assertTrue(hidden.stream().allMatch(e -> e.filletArcs().isEmpty()),
            "preview off → no fillet arcs");

        List<RoadJunctionOverlayEntry> shown = RoadJunctionOverlayController.snapshot(
            network, builder, config, null, "", null, true);
        assertFalse(shown.isEmpty());
        assertTrue(shown.stream().noneMatch(RoadJunctionOverlayEntry::selected),
            "no crossing selected");
        assertTrue(shown.stream().anyMatch(e -> !e.filletArcs().isEmpty()),
            "preview on → fillet arcs without selection");
    }

    @Test
    void snapshotIncludesRegisteredStackCrossings() {
        RoadNetwork network = new RoadNetwork();
        var road = network.createRoad("figure-eight");
        var n1 = network.createNode(new Vec2d(0, 0));
        var n2 = network.createNode(new Vec2d(10, 0));
        network.createEdge(
            n1.getId(),
            n2.getId(),
            List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 10),
                new Vec2d(0, 10),
                new Vec2d(10, 0)),
            road.getId());
        RoadStackCrossingReconciler.reconcileStackCrossings(network);

        List<RoadJunctionOverlayEntry> entries = RoadJunctionOverlayController.snapshot(
            network, new RoadNetworkBuilder(), "", "");

        assertEquals(1, entries.stream().filter(e -> e.source() == IntersectionOverlaySource.STACK_CROSSING).count());
    }
}
