package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;

/**
 * Per-edge mutable endpoint snap state used during {@link com.plot.plugin.road.pipeline.RoadGenerationPipeline}
 * build passes.
 */
public final class EndpointElevationSession {
    private EndpointElevationSnap start;
    private EndpointElevationSnap end;

    public void setSnaps(EndpointElevationSnaps snaps) {
        start = snaps != null ? snaps.start() : null;
        end = snaps != null ? snaps.end() : null;
    }

    public void clear() {
        start = null;
        end = null;
    }

    public int snap(Vec2d center, int targetY) {
        return EndpointElevationBlender.snap(center, targetY, start, end);
    }
}
