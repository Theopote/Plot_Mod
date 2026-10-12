package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.solid.RoadSolidLayer;

/** 设计路面/路肩等表面单元。 */
public record SurfaceCell(
        Vec2d planPoint,
        int surfaceY,
        RoadSolidLayer layer,
        String materialId) {
}
