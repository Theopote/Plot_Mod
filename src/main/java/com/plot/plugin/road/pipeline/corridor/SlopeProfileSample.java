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
        boolean allowWaterEmbankment,
        /** {@link com.plot.plugin.road.pipeline.construction.LocalDaylightCut} deep-cover cut exception. */
        boolean localDaylightAuthorized,
        /** 急弯环带插值格：只补覆盖，不独立触发末端接地/挡墙。 */
        boolean bandInterpolation) {

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
            SlopeConstructionOutcome outcome,
            boolean allowWaterEmbankment,
            boolean bandInterpolation) {
        this(columnKey, planPoint, slopeSurfaceY, fill, materialId, rayId, horizontalOffset,
            slopeRatio, outcome, allowWaterEmbankment, false, bandInterpolation);
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
            SlopeConstructionOutcome outcome,
            boolean allowWaterEmbankment) {
        this(columnKey, planPoint, slopeSurfaceY, fill, materialId, rayId, horizontalOffset,
            slopeRatio, outcome, allowWaterEmbankment, false, false);
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
            slopeRatio, outcome, false, false, false);
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
            slopeRatio, SlopeConstructionOutcome.COMPLETE, false, false, false);
    }
}
