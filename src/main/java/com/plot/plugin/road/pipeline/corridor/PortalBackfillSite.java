package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;

/**
 * One tunnel portal station for {@link TunnelPortalBackfillPass}: real plan geometry,
 * not a collapsed canvas-X bucket.
 */
public record PortalBackfillSite(
        Vec2d center,
        Vec2d leftNormal,
        Vec2d forward,
        int designSurfaceY,
        int envelopeWidthBlocks,
        double unitsPerBlock) {

    public PortalBackfillSite {
        center = center != null ? center.copy() : new Vec2d(0, 0);
        leftNormal = leftNormal != null ? leftNormal.copy() : new Vec2d(0, 1);
        forward = forward != null ? forward.copy() : new Vec2d(1, 0);
    }
}
