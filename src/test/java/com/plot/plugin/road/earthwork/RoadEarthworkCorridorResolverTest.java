package com.plot.plugin.road.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.geometry.RoadCorridorGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void buildCorridorGeometryForClosedRectangle_returnsOuterRing() {
        List<Vec2d> centerline = List.of(
            new Vec2d(0, 0),
            new Vec2d(20, 0),
            new Vec2d(20, 10),
            new Vec2d(0, 10),
            new Vec2d(0, 0));
        RoadCorridorGeometry geometry = RoadEarthworkCorridorResolver.buildCorridorGeometry(
            centerline, 3.0, true);

        assertTrue(geometry.closed());
        assertEquals(geometry.outerContour(), RoadEarthworkCorridorResolver.buildCorridorPolygon(
            centerline, 3.0, true));
        assertFalse(geometry.innerHole().isEmpty());
    }
}
