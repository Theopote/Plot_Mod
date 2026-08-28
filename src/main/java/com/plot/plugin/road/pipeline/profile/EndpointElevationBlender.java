package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;

/**
 * Blends target elevations toward junction endpoint snaps within a blend radius.
 */
public final class EndpointElevationBlender {
    private EndpointElevationBlender() {
    }

    public static int blend(Vec2d center, EndpointElevationSnap snap, int currentY) {
        if (snap == null) {
            return currentY;
        }
        double distance = center.distance(snap.position());
        if (distance >= snap.blendRadius()) {
            return currentY;
        }
        double blend = 1.0 - distance / snap.blendRadius();
        return (int) Math.round(currentY * (1.0 - blend) + snap.elevation() * blend);
    }

    public static int snap(
            Vec2d center,
            int targetY,
            EndpointElevationSnap start,
            EndpointElevationSnap end) {
        int snapped = blend(center, start, targetY);
        return blend(center, end, snapped);
    }
}
