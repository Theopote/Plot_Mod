package com.plot.plugin.road.pipeline.geometry;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PathSegmentGeometryTest {

    @Test
    void leftNormalIsPerpendicularToTangent() {
        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(10, 0));
        Vec2d normal = PathSegmentGeometry.leftNormal(segment);
        Vec2d tangent = segment.end.subtract(segment.start).normalize();

        assertEquals(0.0, normal.dot(tangent), 1e-9);
        assertEquals(1.0, normal.length(), 1e-9);
    }

    @Test
    void chainLeftNormalFlipsWhenChainRunsBackwardAlongGeometry() {
        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(10, 0));
        Vec2d geometryLeft = PathSegmentGeometry.leftNormal(segment);
        Vec2d chainLeft = PathSegmentGeometry.chainLeftNormal(segment, true);
        Vec2d chainRight = PathSegmentGeometry.chainLeftNormal(segment, false);

        assertEquals(geometryLeft.x, chainLeft.x, 1e-9);
        assertEquals(geometryLeft.y, chainLeft.y, 1e-9);
        assertEquals(-geometryLeft.x, chainRight.x, 1e-9);
        assertEquals(-geometryLeft.y, chainRight.y, 1e-9);
    }
}
