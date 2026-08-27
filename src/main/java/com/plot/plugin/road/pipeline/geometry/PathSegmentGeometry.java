package com.plot.plugin.road.pipeline.geometry;

import com.plot.api.geometry.Vec2d;

public final class PathSegmentGeometry {
    private PathSegmentGeometry() {
    }

    public static Vec2d leftNormal(PathSegment segment) {
        Vec2d tangent = segment.end.subtract(segment.start);
        if (tangent.lengthSquared() < 1e-12) {
            return new Vec2d(0, 1);
        }
        Vec2d unit = tangent.normalize();
        return new Vec2d(-unit.y, unit.x);
    }
}
