package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.pipeline.geometry.PathSegment;

/**
 * Per-segment ground and target elevations after longitudinal profile solving.
 */
public final class SegmentHeightInfo {
    public final PathSegment segment;
    public final int groundStart;
    public final int groundEnd;
    public final int targetStart;
    public final int targetEnd;
    public final double slope;

    public SegmentHeightInfo(
            PathSegment segment,
            int groundStart,
            int groundEnd,
            int targetStart,
            int targetEnd,
            double slope) {
        this.segment = segment;
        this.groundStart = groundStart;
        this.groundEnd = groundEnd;
        this.targetStart = targetStart;
        this.targetEnd = targetEnd;
        this.slope = slope;
    }
}
