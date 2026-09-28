package com.plot.plugin.road.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadProfileIntersectionDragEditorTest {

    @Test
    void draggingOtherRoadUpdatesItsPviWhenGradeSeparated() {
        RoadNetwork network = new RoadNetwork();
        RoadSystemConfig config = new RoadSystemConfig("test");
        Road roadA = network.createRoad("Highway");
        Road roadB = network.createRoad("Local");
        roadA.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 76),
            PointOfVerticalIntersection.of(100, 76))));
        roadB.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 68),
            PointOfVerticalIntersection.of(50, 68))));

        var west = network.createNode(new Vec2d(0, 0));
        var center = network.createNode(new Vec2d(50, 0));
        var east = network.createNode(new Vec2d(100, 0));
        var north = network.createNode(new Vec2d(50, 50));
        network.createEdge(west.getId(), center.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(50, 0)), roadA.getId());
        network.createEdge(center.getId(), east.getId(),
            List.of(new Vec2d(50, 0), new Vec2d(100, 0)), roadA.getId());
        network.createEdge(center.getId(), north.getId(),
            List.of(new Vec2d(50, 0), new Vec2d(50, 50)), roadB.getId());
        network.setNodeGradeSeparation(center.getId(), true, roadA.getId(), 4.0);

        RoadProfileIntersection intersection = gradeSeparatedIntersection(
            config, center.getId(), roadA.getId(), roadB.getId(), 76.0, 68.0);

        assertTrue(RoadProfileIntersectionDragEditor.applyDraggedElevation(
            network, roadA, intersection,
            RoadProfileIntersectionDragEditor.DragTarget.OTHER,
            70.0,
            config));

        assertEquals(
            70.0,
            VerticalAlignmentGeometry.elevationAt(roadB.getVerticalAlignment(), 0.0).orElse(Double.NaN),
            1e-6);
        assertEquals(RoadVerticalMode.MANUAL_PROFILE, roadB.getVerticalMode());
    }

    @Test
    void draggingOtherRoadDoesNotRewriteRequiredClearance() {
        RoadNetwork network = new RoadNetwork();
        RoadSystemConfig config = new RoadSystemConfig("test");
        Road roadA = network.createRoad("Highway");
        Road roadB = network.createRoad("Local");
        roadA.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 80),
            PointOfVerticalIntersection.of(100, 80))));
        roadB.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(50, 70))));

        var west = network.createNode(new Vec2d(0, 0));
        var center = network.createNode(new Vec2d(50, 0));
        var east = network.createNode(new Vec2d(100, 0));
        var north = network.createNode(new Vec2d(50, 50));
        network.createEdge(west.getId(), center.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(50, 0)), roadA.getId());
        network.createEdge(center.getId(), east.getId(),
            List.of(new Vec2d(50, 0), new Vec2d(100, 0)), roadA.getId());
        network.createEdge(center.getId(), north.getId(),
            List.of(new Vec2d(50, 0), new Vec2d(50, 50)), roadB.getId());
        RoadNode centerNode = network.getNode(center.getId());
        centerNode.setGradeSeparated(true);
        centerNode.setElevatedRoadId(roadA.getId());
        centerNode.setCrossingClearance(4.0);
        assertEquals(4.0, centerNode.getCrossingClearance(), 1e-6);

        RoadProfileIntersection intersection = gradeSeparatedIntersection(
            config, center.getId(), roadA.getId(), roadB.getId(), 80.0, 70.0);

        assertTrue(RoadProfileIntersectionDragEditor.applyDraggedElevation(
            network, roadA, intersection,
            RoadProfileIntersectionDragEditor.DragTarget.OTHER,
            72.0,
            config));

        assertEquals(4.0, centerNode.getCrossingClearance(), 1e-6);
        assertEquals(72.0,
            VerticalAlignmentGeometry.elevationAt(roadB.getVerticalAlignment(), 0.0).orElse(Double.NaN),
            1e-6);
    }

    @Test
    void clampsOtherRoadBelowRequiredClearanceWhenCurrentIsElevated() {
        RoadSystemConfig config = new RoadSystemConfig("test");

        assertEquals(
            76.0,
            RoadProfileIntersectionDragEditor.clampOtherGradeSeparatedElevation(
                80.0, true, 4.0, 78.0),
            1e-6);
    }

    private static RoadProfileIntersection gradeSeparatedIntersection(
            RoadSystemConfig config,
            String nodeId,
            String currentRoadId,
            String otherRoadId,
            double currentElevation,
            double otherElevation) {
        return new RoadProfileIntersection(
            nodeId,
            currentRoadId,
            otherRoadId,
            "Other",
            50.0,
            50.0,
            currentElevation,
            otherElevation,
            ResolvedCrossSection.fromConfig(config),
            true,
            true,
            4.0,
            false);
    }
}
