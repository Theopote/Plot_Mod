package com.plot.plugin.road.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

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

        RoadProfileIntersection intersection = new RoadProfileIntersection(
            center.getId(),
            roadA.getId(),
            roadB.getId(),
            "Local",
            50.0,
            50.0,
            76.0,
            68.0,
            ResolvedCrossSection.fromConfig(config),
            true,
            true,
            4.0,
            false);

        assertTrue(RoadProfileIntersectionDragEditor.applyDraggedElevation(
            network, roadA, intersection,
            RoadProfileIntersectionDragEditor.DragTarget.OTHER,
            70.0,
            config));

        OptionalInt junctionPvi = RoadProfileIntersectionDragEditor.junctionPviIndex(
            network, roadB, center.getId());
        assertTrue(junctionPvi.isPresent());
        assertEquals(
            70.0,
            roadB.getVerticalAlignment().getPvis().get(junctionPvi.getAsInt()).getElevation(),
            1e-6);
        assertEquals(RoadVerticalMode.MANUAL_PROFILE, roadB.getVerticalMode());
    }

    @Test
    void clampsOtherRoadBelowClearanceWhenCurrentIsElevated() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadProfileIntersection intersection = new RoadProfileIntersection(
            "node",
            "a",
            "b",
            "B",
            10.0,
            10.0,
            80.0,
            70.0,
            ResolvedCrossSection.fromConfig(config),
            true,
            true,
            4.0,
            false);

        assertEquals(
            76.0,
            RoadProfileIntersectionDragEditor.clampOtherGradeSeparatedElevation(
                new RoadProfileIntersection(
                    "node", "a", "b", "B", 10.0, 10.0, 80.0, 70.0,
                    ResolvedCrossSection.fromConfig(config),
                    true, true, 4.0, false),
                78.0, config),
            1e-6);
    }
}
