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

    /**
     * 相对道路链方向的左侧法向：{@code forward == false} 时与几何 leftNormal 相反。
     */
    public static Vec2d chainLeftNormal(PathSegment segment, boolean forward) {
        Vec2d geometryLeft = leftNormal(segment);
        return forward ? geometryLeft : geometryLeft.multiply(-1);
    }
}
