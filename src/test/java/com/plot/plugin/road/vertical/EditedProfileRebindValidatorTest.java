package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EditedProfileRebindValidatorTest {

    private final RoadSystemConfig config = new RoadSystemConfig("test");

    @Test
    void acceptsCurveThatCoversCurrentPath() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoad("r1");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(50.0, 66.0),
            PointOfVerticalIntersection.of(100.0, 64.0))));

        assertNull(EditedProfileRebindValidator.validate(network, road, config));
    }

    @Test
    void rejectsCurveThatEndsBeforePathLength() {
        RoadNetwork network = straightRoad(180.0);
        Road road = network.getRoad("r1");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(100.0, 68.0))));

        assertSame(
            EditedProfileRebindValidator.Failure.STATIONS_DO_NOT_COVER_PATH,
            EditedProfileRebindValidator.validate(network, road, config));
    }

    @Test
    void rejectsCurveThatExceedsMaxGrade() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoad("r1");
        road.setMaxSlope(5.0f);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(100.0, 90.0))));

        assertSame(
            EditedProfileRebindValidator.Failure.EXCEEDS_MAX_GRADE,
            EditedProfileRebindValidator.validate(network, road, config));
    }

    @Test
    void rejectsAtGradeCrossingMismatch() {
        RoadNetwork network = new RoadNetwork();
        Road a = network.createRoad("a");
        Road b = network.createRoad("b");
        a.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(100.0, 70.0))));
        b.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(100.0, 64.0))));
        RoadNode west = network.createNode(new Vec2d(0, 0));
        RoadNode junction = network.createNode(new Vec2d(50, 0));
        RoadNode east = network.createNode(new Vec2d(100, 0));
        network.createEdge(west.getId(), junction.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(50, 0)), a.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(new Vec2d(50, 0), new Vec2d(100, 0)), a.getId());
        RoadNode south = network.createNode(new Vec2d(50, -50));
        network.createEdge(south.getId(), junction.getId(),
            List.of(new Vec2d(50, -50), new Vec2d(50, 0)), b.getId());
        network.registerCrossing(com.plot.plugin.road.crossing.RoadCrossing.atGrade(
            a.getId(), 50.0, b.getId(), 50.0, new Vec2d(50, 0)));

        assertSame(
            EditedProfileRebindValidator.Failure.CONSTRAINT_CONFLICT,
            EditedProfileRebindValidator.validate(network, a, config));
    }

    private static RoadNetwork straightRoad(double length) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(length, 0));
        network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(length, 0)),
            road.getId());
        return network;
    }
}
