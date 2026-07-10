package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadEdge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadJunctionGeometryTest {

    @Test
    void collectPolygonVerticesBuildsTJunctionOutline() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(6);

        RoadEdge north = new RoadEdge("e-n", "junction", "n-end", List.of(
            new Vec2d(0, 0),
            new Vec2d(0, 10)
        ), null, null, null, null, null, null, null, null);
        RoadEdge east = new RoadEdge("e-e", "junction", "e-end", List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0)
        ), null, null, null, null, null, null, null, null);
        RoadEdge south = new RoadEdge("e-s", "s-end", "junction", List.of(
            new Vec2d(0, -10),
            new Vec2d(0, 0)
        ), null, null, null, null, null, null, null, null);

        List<Vec2d> polygon = RoadJunctionGeometry.collectPolygonVertices(
            "junction",
            List.of(north, east, south),
            edge -> edge.getEffectiveWidth(config) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS
        );

        assertTrue(polygon.size() >= 3);
        assertTrue(polygon.stream().anyMatch(point -> point.y > 0),
            "north-bound edge should contribute a positive-Y offset vertex");
        assertTrue(polygon.stream().anyMatch(point -> point.x > 0),
            "east-bound edge should contribute a positive-X offset vertex");
    }

    @Test
    void extractNearNodeSegmentWalksAwayFromJunctionAlongCenterline() {
        RoadEdge edge = new RoadEdge("edge", "junction", "far", List.of(
            new Vec2d(0, 0),
            new Vec2d(0, 4),
            new Vec2d(0, 12)
        ), null, null, null, null, null, null, null, null);

        List<Vec2d> segment = RoadJunctionGeometry.extractNearNodeSegment(
            edge, "junction", 3.0);

        assertEquals(3, segment.size());
        assertEquals(0, segment.getFirst().x, 1e-6);
        assertEquals(0, segment.getFirst().y, 1e-6);
        assertEquals(4, segment.get(1).y, 1e-6);
        assertTrue(segment.get(2).y >= 2.9 && segment.get(2).y <= 3.1);
    }

    @Test
    void collectSimpleEnvelopePointsFillsCircularArea() {
        List<Vec2d> points = RoadJunctionGeometry.collectSimpleEnvelopePoints(new Vec2d(0, 0), 2);

        assertEquals(13, points.size());
        assertTrue(points.stream().anyMatch(point -> point.x == 0 && point.y == 0));
        assertFalse(points.stream().anyMatch(point -> point.x == 3 && point.y == 0));
    }
}
