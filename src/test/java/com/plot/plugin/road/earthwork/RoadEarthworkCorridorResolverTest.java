package com.plot.plugin.road.earthwork;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadEarthworkCorridorResolverTest {

    @Test
    void buildCorridorPolygonForStraightCenterline() {
        List<Vec2d> centerline = List.of(new Vec2d(0, 0), new Vec2d(10, 0));
        List<Vec2d> polygon = RoadEarthworkCorridorResolver.buildCorridorPolygon(centerline, 3.0);

        assertTrue(polygon.size() >= 4);
        assertTrue(polygon.stream().anyMatch(point -> Math.abs(point.y - 3.0) < 1e-6));
        assertTrue(polygon.stream().anyMatch(point -> Math.abs(point.y + 3.0) < 1e-6));
    }
}
