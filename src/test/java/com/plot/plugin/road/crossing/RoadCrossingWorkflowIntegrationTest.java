package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.overlay.IntersectionHit;
import com.plot.plugin.road.overlay.IntersectionOverlaySource;
import com.plot.plugin.road.overlay.RoadJunctionOverlayController;
import com.plot.plugin.road.overlay.RoadJunctionOverlayEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCrossingWorkflowIntegrationTest {
    private RoadNetworkManager manager;
    private RoadNetworkBuilder builder;

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
        builder = manager.getNetworkBuilder();
    }

    @Test
    void adoptIntersectingPaths_registersCrossingWithoutSharedJunctionNode() {
        manager.adoptSelectedPaths(List.of(
            new PolylineShape(List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false),
            new PolylineShape(List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false)));

        RoadNetwork network = manager.getNetwork();
        assertEquals(2, network.getRoads().size());
        assertEquals(1, network.getCrossings().size());

        long junctionNodes = network.getNodes().values().stream()
            .filter(node -> node != null && node.getDegree() >= 3)
            .count();
        assertEquals(0, junctionNodes);
    }

    @Test
    void crossingAppearsInOverlayAndCanBeSelected() {
        manager.adoptSelectedPaths(List.of(
            new PolylineShape(List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false),
            new PolylineShape(List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false)));

        RoadNetwork network = manager.getNetwork();
        RoadCrossing crossing = network.getCrossings().values().iterator().next();

        List<RoadJunctionOverlayEntry> entries = RoadJunctionOverlayController.snapshot(
            network,
            builder,
            "",
            "");
        assertEquals(1, entries.size());
        assertEquals(IntersectionOverlaySource.CROSSING, entries.getFirst().source());
        assertEquals(crossing.id(), entries.getFirst().sourceId());

        Vec2d position = crossing.position();
        IntersectionHit hit = RoadJunctionOverlayController.hitTest(
            entries, position.x, position.y, 1.0);
        assertNotNull(hit);
        assertEquals(IntersectionOverlaySource.CROSSING, hit.source());
        assertEquals(crossing.id(), hit.id());

        manager.handleCrossingSelect(hit.id());
        assertNotNull(manager.getSelectedCrossing());
        assertEquals(crossing.id(), manager.getSelectedCrossing().id());
    }

    @Test
    void focusIntersection_highlightsRoadsWithoutClearingCrossingSelection() {
        manager.adoptSelectedPaths(List.of(
            new PolylineShape(List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false),
            new PolylineShape(List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false)));

        RoadCrossing crossing = manager.getNetwork().getCrossings().values().iterator().next();
        manager.focusIntersection(IntersectionOverlaySource.CROSSING, crossing.id());

        assertEquals(crossing.id(), manager.getSelectedCrossingId());
        assertFalse(manager.getSelectedEdgeIds().isEmpty());
    }

    @Test
    void reconcilePreservesGradeSeparationDesign() {
        manager.adoptSelectedPaths(List.of(
            new PolylineShape(List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false),
            new PolylineShape(List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false)));

        RoadNetwork network = manager.getNetwork();
        RoadCrossing original = network.getCrossings().values().iterator().next();
        String verticalRoadId = Math.abs(original.stationA() - 5.0) < Math.abs(original.stationB() - 5.0)
            ? original.roadAId() : original.roadBId();

        network.setCrossingGradeSeparation(
            original.id(),
            CrossingType.GRADE_SEPARATED,
            verticalRoadId,
            6.0);

        manager.reconcileCrossings();

        RoadCrossing preserved = network.getCrossings().values().iterator().next();
        assertEquals(original.id(), preserved.id());
        assertEquals(CrossingType.GRADE_SEPARATED, preserved.type());
        assertEquals(verticalRoadId, preserved.elevatedRoadId());
        assertEquals(6.0, preserved.crossingClearance(), 1e-6);
    }
}
