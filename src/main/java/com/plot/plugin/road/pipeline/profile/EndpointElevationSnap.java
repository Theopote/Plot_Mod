package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;

/**
 * @param blendRadius distance from {@code position} beyond which the snap has no effect
 * @param coreRadius  distance within which Y is the snap elevation (plaza / trim footprint)
 */
public record EndpointElevationSnap(Vec2d position, int elevation, double blendRadius, double coreRadius) {
    public EndpointElevationSnap(Vec2d position, int elevation, double blendRadius) {
        this(position, elevation, blendRadius, 0.0);
    }

    public EndpointElevationSnap {
        if (!Double.isFinite(coreRadius) || coreRadius < 0.0) {
            coreRadius = 0.0;
        }
        if (!Double.isFinite(blendRadius) || blendRadius < coreRadius) {
            blendRadius = coreRadius;
        }
    }
}
