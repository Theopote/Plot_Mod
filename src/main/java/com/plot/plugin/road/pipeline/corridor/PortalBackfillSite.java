package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;

/**
 * One tunnel portal station for {@link TunnelPortalBackfillPass}: real plan geometry,
 * not a collapsed canvas-X bucket.
 * <p>
 * {@code forward} points toward increasing chainage (station-increasing tangent).
 * {@code intoHillSign}: +1 / -1 along {@link #forward()} toward tunnel interior;
 * 0 means unknown (backfill pass falls back to column voting).
 */
public record PortalBackfillSite(
        Vec2d center,
        Vec2d leftNormal,
        Vec2d forward,
        int designSurfaceY,
        int envelopeWidthBlocks,
        double unitsPerBlock,
        int intoHillSign) {

    public PortalBackfillSite(
            Vec2d center,
            Vec2d leftNormal,
            Vec2d forward,
            int designSurfaceY,
            int envelopeWidthBlocks,
            double unitsPerBlock) {
        this(center, leftNormal, forward, designSurfaceY, envelopeWidthBlocks, unitsPerBlock, 0);
    }

    public PortalBackfillSite {
        center = center != null ? center.copy() : new Vec2d(0, 0);
        leftNormal = leftNormal != null ? leftNormal.copy() : new Vec2d(0, 1);
        forward = forward != null ? forward.copy() : new Vec2d(1, 0);
        if (intoHillSign > 1) {
            intoHillSign = 1;
        } else if (intoHillSign < -1) {
            intoHillSign = -1;
        }
    }
}
