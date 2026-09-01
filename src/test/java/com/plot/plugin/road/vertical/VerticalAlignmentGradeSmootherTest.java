package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalAlignmentGradeSmootherTest {

    @Test
    void smoothsSteepTwoPviProfile() {
        RoadVerticalAlignment steep = new RoadVerticalAlignment(java.util.List.of(
            PointOfVerticalIntersection.of(0.0, 0.0),
            PointOfVerticalIntersection.of(100.0, 20.0)
        ));

        VerticalAlignmentGradeSmoother.Result result =
            VerticalAlignmentGradeSmoother.smooth(steep, 5.0f);

        assertTrue(result.changed());
        assertTrue(result.withinLimit());
        assertEquals(0.0, result.alignment().getPvis().getFirst().getElevation(), 1e-6);
        assertEquals(5.0, result.alignment().getPvis().getLast().getElevation(), 0.05);
    }

    @Test
    void leavesCompliantProfileUnchanged() {
        RoadVerticalAlignment gentle = new RoadVerticalAlignment(java.util.List.of(
            PointOfVerticalIntersection.of(0.0, 0.0),
            PointOfVerticalIntersection.of(100.0, 4.0)
        ));

        VerticalAlignmentGradeSmoother.Result result =
            VerticalAlignmentGradeSmoother.smooth(gentle, 5.0f);

        assertFalse(result.changed());
        assertTrue(result.withinLimit());
    }

    @Test
    void smoothAllExceedingCountsFixedRoads() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setMaxSlope(5.0f);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("steep");
        road.setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            PointOfVerticalIntersection.of(0.0, 0.0),
            PointOfVerticalIntersection.of(50.0, 10.0)
        )));
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(50, 0));
        network.createEdge(
            a.getId(), b.getId(), java.util.List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());

        assertEquals(1, VerticalAlignmentGradeSmoother.smoothAllExceeding(network, config));
    }
}
