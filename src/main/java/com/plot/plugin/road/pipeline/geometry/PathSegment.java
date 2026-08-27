package com.plot.plugin.road.pipeline.geometry;

import com.plot.api.geometry.Vec2d;

/**
 * A sampled segment along a road centerline polyline.
 */
public final class PathSegment {
    public final Vec2d start;
    public final Vec2d end;
    public final double distance;

    public PathSegment(Vec2d start, Vec2d end) {
        this.start = start;
        this.end = end;
        this.distance = start.distance(end);
    }
}
