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
        float slopeRatio,
        SlopeConstructionOutcome outcome,
        boolean allowWaterEmbankment) {

    public SlopeProfileSample {
        outcome = outcome != null ? outcome : SlopeConstructionOutcome.COMPLETE;
    }

    public SlopeProfileSample(
            ColumnKey columnKey,
            Vec2d planPoint,
            int slopeSurfaceY,
            boolean fill,
            String materialId,
            SlopeRayId rayId,
            int horizontalOffset,
            float slopeRatio,
            SlopeConstructionOutcome outcome) {
        this(columnKey, planPoint, slopeSurfaceY, fill, materialId, rayId, horizontalOffset,
            slopeRatio, outcome, false);
    }

    public SlopeProfileSample(
            ColumnKey columnKey,
            Vec2d planPoint,
            int slopeSurfaceY,
            boolean fill,
            String materialId,
            SlopeRayId rayId,
            int horizontalOffset,
            float slopeRatio) {
        this(columnKey, planPoint, slopeSurfaceY, fill, materialId, rayId, horizontalOffset,
            slopeRatio, SlopeConstructionOutcome.COMPLETE, false);
    }
}
