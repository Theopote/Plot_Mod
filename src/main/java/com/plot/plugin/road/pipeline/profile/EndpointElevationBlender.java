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
        double coreRadius = snap.coreRadius();
        double blendRadius = snap.blendRadius();
        if (distance <= coreRadius) {
            return snap.elevation();
        }
        if (distance >= blendRadius) {
            return currentY;
        }
        double span = blendRadius - coreRadius;
        double t = span > 1e-9 ? 1.0 - (distance - coreRadius) / span : 1.0;
        double blend = smoothStep(t);
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

    private static double smoothStep(double t) {
        if (t <= 0.0) {
            return 0.0;
        }
        if (t >= 1.0) {
            return 1.0;
        }
        return t * t * (3.0 - 2.0 * t);
    }
}
