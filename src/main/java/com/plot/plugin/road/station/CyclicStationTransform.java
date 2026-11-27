package com.plot.plugin.road.station;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified cyclic station math for LOOP roads: wrap, origin/direction, and interval split.
 * Failures return {@link Double#NaN} — never a legal zero station as a sentinel.
 */
public final class CyclicStationTransform {

    private static final double EPSILON = 1e-9;

    private CyclicStationTransform() {
    }

    /** Wrap into {@code [0, length)}; invalid input → NaN. */
    public static double wrap(double value, double length) {
        if (!Double.isFinite(value) || !Double.isFinite(length) || length <= EPSILON) {
            return Double.NaN;
        }
        double result = value;
        while (result >= length - EPSILON) {
            result -= length;
        }
        while (result < -EPSILON) {
            result += length;
        }
        if (result < 0.0) {
            result = 0.0;
        }
        if (result > length) {
            result = length;
        }
        // Prefer exact 0 over exact L for open-point semantics when nearly equal.
        if (Math.abs(result - length) <= EPSILON) {
            return 0.0;
        }
        return result;
    }

    /**
     * Canonical/rotated station {@code s} → native/physical: {@code n = wrap(o + σ·s, L)}.
     *
     * @param sigma {@code +1} same circulation, {@code -1} opposed
     */
    public static double fromOrigin(double origin, int sigma, double station, double length) {
        if (sigma != 1 && sigma != -1) {
            return Double.NaN;
        }
        if (!Double.isFinite(origin) || !Double.isFinite(station)) {
            return Double.NaN;
        }
        return wrap(origin + (double) sigma * station, length);
    }

    /**
     * Native/physical {@code n} → canonical/rotated: {@code s = wrap(σ·(n − o), L)}.
     */
    public static double toOrigin(double origin, int sigma, double nativeOrPhysical, double length) {
        if (sigma != 1 && sigma != -1) {
            return Double.NaN;
        }
        if (!Double.isFinite(origin) || !Double.isFinite(nativeOrPhysical)) {
            return Double.NaN;
        }
        return wrap((double) sigma * (nativeOrPhysical - origin), length);
    }

    /**
     * Seam-migrate a point station: {@code s' = wrap(s − shift, L)}.
     * Invalid → NaN (not 0).
     */
    public static double rotateByShift(double station, double shift, double length) {
        if (!Double.isFinite(station) || !Double.isFinite(shift)) {
            return Double.NaN;
        }
        return wrap(station - shift, length);
    }

    /**
     * Cyclic interval migrate preserving coverage length.
     * Returns zero, one, or two intervals with {@code start < end} in {@code [0, length]}.
     * Passing {@code end == null} is not supported — resolve to a finite end first.
     */
    public static List<double[]> rotateInterval(
            double start,
            double end,
            double shift,
            double length) {
        List<double[]> result = new ArrayList<>(2);
        if (!Double.isFinite(start) || !Double.isFinite(end) || !Double.isFinite(shift)
                || !Double.isFinite(length) || length <= EPSILON) {
            return result;
        }
        if (end <= start + EPSILON) {
            return result;
        }
        double coverage = end - start;
        if (coverage > length + EPSILON) {
            return result;
        }
        double newStart = rotateByShift(start, shift, length);
        if (!Double.isFinite(newStart)) {
            return result;
        }
        double newEnd = newStart + coverage;
        if (newEnd <= length + EPSILON) {
            result.add(new double[] {newStart, Math.min(newEnd, length)});
            return result;
        }
        // Wraps past L: [newStart, L] ∪ [0, overflow].
        if (newStart < length - EPSILON) {
            result.add(new double[] {newStart, length});
        }
        double overflow = newEnd - length;
        if (overflow > EPSILON) {
            result.add(new double[] {0.0, Math.min(overflow, length)});
        }
        return result;
    }
}
