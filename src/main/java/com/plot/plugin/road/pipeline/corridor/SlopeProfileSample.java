package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;

/** One outward batter step before column consolidation. */
public record SlopeProfileSample(
        ColumnKey columnKey,
        Vec2d planPoint,
        int slopeSurfaceY,
        boolean fill,
        String materialId,
        SlopeRayId rayId,
        int horizontalOffset,
        boolean incomplete) {

    public SlopeProfileSample(
            ColumnKey columnKey,
            Vec2d planPoint,
            int slopeSurfaceY,
            boolean fill,
            String materialId,
            SlopeRayId rayId,
            int horizontalOffset) {
        this(columnKey, planPoint, slopeSurfaceY, fill, materialId, rayId, horizontalOffset, false);
    }
}
