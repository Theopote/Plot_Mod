package com.plot.plugin.road.pipeline.geometry;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadGeometrySamplerTest {

    @Test
    void sampleSubdividesLongSegments() {
        List<Vec2d> path = List.of(
            new Vec2d(0, 0),
            new Vec2d(20, 0));

        List<PathSegment> segments = RoadGeometrySampler.sample(
            path,
            5.0,
            (points, ignored) -> 1.0);

        assertTrue(segments.size() >= 4);
        assertEquals(new Vec2d(0, 0), segments.getFirst().start);
        assertEquals(new Vec2d(20, 0), segments.getLast().end);
    }

    @Test
    void sampleKeepsShortSegmentsAsSinglePiece() {
        List<Vec2d> path = List.of(
            new Vec2d(0, 0),
            new Vec2d(2, 0));

        List<PathSegment> segments = RoadGeometrySampler.sample(
            path,
            5.0,
            (points, ignored) -> 1.0);

        assertEquals(1, segments.size());
        assertEquals(2.0, segments.getFirst().distance, 1e-9);
    }
}
