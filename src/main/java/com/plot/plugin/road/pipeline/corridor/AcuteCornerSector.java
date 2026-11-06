package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;

import java.util.function.Consumer;

/**
 * Fixed-center angular sector for shared polyline vertices where consecutive stations
 * share a world column but change left-normal. Distance-based densify cannot fill that
 * triangular gore; slope fans already use the same pattern.
 */
public final class AcuteCornerSector {
    /** Outer-ring angular gap target in blocks (matches slope fan). */
    public static final double FAN_GAP_BLOCKS = 1.25;
    public static final int FAN_MAX_STEPS = 24;
    /** Match {@link CrossSectionSpanRasterizer} CLIP_TURN — significant enough to fan. */
    public static final double SIGNIFICANT_TURN_RADIANS = Math.toRadians(25.0);
    /** Match tunnel acute skip (~90° polyline turn). */
    public static final double ACUTE_TURN_RADIANS = Math.PI * 0.5;
    public static final double SKIP_RADIUS_FACTOR = 1.25;

    private AcuteCornerSector() {
    }

    public static double skipRadius(double scale) {
        double s = Math.max(scale, 1e-9);
        return Math.max(s, SKIP_RADIUS_FACTOR * s);
    }

    public static Vec2d normalize(Vec2d vector) {
        if (vector == null || vector.lengthSquared() < 1e-12) {
            return new Vec2d(0, 1);
        }
        return vector.normalize();
    }

    public static double signedAngle(Vec2d from, Vec2d to) {
        Vec2d a = normalize(from);
        Vec2d b = normalize(to);
        if (a.lengthSquared() < 1e-12 || b.lengthSquared() < 1e-12) {
            return 0.0;
        }
        return Math.atan2(a.cross(b), a.dot(b));
    }

    /**
     * Angular stamp count so outer arc (~halfWidth) gaps stay near {@link #FAN_GAP_BLOCKS}.
     * Used for pavement / open clearance fans.
     */
    public static int angularSteps(double turnRadians, double halfWidthBlocks) {
        double absTurn = Math.abs(turnRadians);
        if (absTurn < 1e-9) {
            return 0;
        }
        double run = Math.max(1.0, halfWidthBlocks);
        double maxGapRadians = FAN_GAP_BLOCKS / run;
        return Math.max(1, Math.min(FAN_MAX_STEPS, (int) Math.ceil(absTurn / maxGapRadians)));
    }

    /**
     * Coarser ~45° increments for tunnel lining/cavity fans. Dense pavement-style fans
     * stack conflicting shell vs air stamps and leave isolated lining voxels.
     */
    public static int tunnelAngularSteps(double turnRadians) {
        double absTurn = Math.abs(turnRadians);
        if (absTurn < 1e-9) {
            return 0;
        }
        return Math.max(1, Math.min(FAN_MAX_STEPS,
            (int) Math.ceil(absTurn / Math.toRadians(45.0))));
    }

    public static boolean needsAngularFan(double turnRadians) {
        return Math.abs(turnRadians) >= SIGNIFICANT_TURN_RADIANS;
    }

    /**
     * Emit interpolated left-normals from {@code fromNormal} to {@code toNormal} inclusive.
     */
    public static void forEachNormal(
            Vec2d fromNormal,
            Vec2d toNormal,
            double halfWidthBlocks,
            Consumer<Vec2d> consumer) {
        forEachNormal(fromNormal, toNormal, angularSteps(signedAngle(fromNormal, toNormal), halfWidthBlocks), consumer);
    }

    /** Tunnel cavity/lining fan with coarse angular steps. */
    public static void forEachTunnelNormal(
            Vec2d fromNormal,
            Vec2d toNormal,
            Consumer<Vec2d> consumer) {
        forEachNormal(fromNormal, toNormal, tunnelAngularSteps(signedAngle(fromNormal, toNormal)), consumer);
    }

    public static void forEachNormal(
            Vec2d fromNormal,
            Vec2d toNormal,
            int steps,
            Consumer<Vec2d> consumer) {
        if (consumer == null) {
            return;
        }
        Vec2d start = normalize(fromNormal);
        Vec2d end = normalize(toNormal);
        double turn = signedAngle(start, end);
        if (!needsAngularFan(turn) || steps <= 0) {
            consumer.accept(end);
            return;
        }
        int resolvedSteps = Math.max(1, steps);
        for (int i = 0; i <= resolvedSteps; i++) {
            double t = (double) i / resolvedSteps;
            consumer.accept(normalize(start.rotate(turn * t)));
        }
    }
}
