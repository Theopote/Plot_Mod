package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.RoadSlopeUtils;
import com.plot.plugin.road.pipeline.geometry.PathSegment;

/**
 * Per-segment ground, design, and build elevations after longitudinal profile solving.
 */
public final class SegmentHeightInfo {
    public final PathSegment segment;
    public final int groundStart;
    public final int groundEnd;
    public final Integer waterStart;
    public final Integer waterEnd;
    public final double designStart;
    public final double designEnd;
    public final int targetStart;
    public final int targetEnd;
    /** Engineering grade from continuous design endpoints. */
    public final double slope;
    /** Apparent grade between rasterized build endpoints (diagnostic). */
    public final double buildSlope;

    public SegmentHeightInfo(
            PathSegment segment,
            int groundStart,
            int groundEnd,
            int targetStart,
            int targetEnd,
            double slope) {
        this(
            segment,
            groundStart,
            groundEnd,
            null,
            null,
            targetStart,
            targetEnd,
            targetStart,
            targetEnd,
            slope,
            slope);
    }

    public SegmentHeightInfo(
            PathSegment segment,
            int groundStart,
            int groundEnd,
            int targetStart,
            int targetEnd,
            double designStart,
            double designEnd,
            double segmentDistanceWorld) {
        this(
            segment,
            groundStart,
            groundEnd,
            null,
            null,
            targetStart,
            targetEnd,
            designStart,
            designEnd,
            RoadSlopeUtils.computeActualSlopePercent(designStart, designEnd, segmentDistanceWorld),
            RoadSlopeUtils.computeActualSlopePercent(targetStart, targetEnd, segmentDistanceWorld));
    }

    public SegmentHeightInfo(
            PathSegment segment,
            int groundStart,
            int groundEnd,
            Integer waterStart,
            Integer waterEnd,
            int targetStart,
            int targetEnd,
            double designStart,
            double designEnd,
            double segmentDistanceWorld) {
        this(
            segment,
            groundStart,
            groundEnd,
            waterStart,
            waterEnd,
            targetStart,
            targetEnd,
            designStart,
            designEnd,
            RoadSlopeUtils.computeActualSlopePercent(designStart, designEnd, segmentDistanceWorld),
            RoadSlopeUtils.computeActualSlopePercent(targetStart, targetEnd, segmentDistanceWorld));
    }

    private SegmentHeightInfo(
            PathSegment segment,
            int groundStart,
            int groundEnd,
            Integer waterStart,
            Integer waterEnd,
            int targetStart,
            int targetEnd,
            double designStart,
            double designEnd,
            double slope,
            double buildSlope) {
        this.segment = segment;
        this.groundStart = groundStart;
        this.groundEnd = groundEnd;
        this.waterStart = waterStart;
        this.waterEnd = waterEnd;
        this.designStart = designStart;
        this.designEnd = designEnd;
        this.targetStart = targetStart;
        this.targetEnd = targetEnd;
        this.slope = slope;
        this.buildSlope = buildSlope;
    }
}
