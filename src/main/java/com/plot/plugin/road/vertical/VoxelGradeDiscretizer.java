package com.plot.plugin.road.vertical;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/** Converts continuous design elevations into deterministic Minecraft block steps. */
public final class VoxelGradeDiscretizer {
    private static final double EPSILON = 1e-9;
    private VoxelGradeDiscretizer() { }

    public static int quantizeContinuous(double elevation) {
        if (!Double.isFinite(elevation)) return 0;
        return (int) Math.round(elevation);
    }

    /** Bresenham-style distribution for a linear run; ratio is clamped to [0, 1]. */
    public static int linearElevationAtRatio(int startY, int endY, double ratio) {
        double clamped = Math.max(0.0, Math.min(1.0, ratio));
        int delta = endY - startY;
        int steps = (int) Math.floor(clamped * Math.abs(delta) + EPSILON);
        return startY + Integer.signum(delta) * steps;
    }

    /** Samples a continuous profile at every block station, including both endpoints. */
    public static List<Integer> discretizeContinuousProfile(
            double length,
            DoubleUnaryOperator elevationAtStation) {
        if (!Double.isFinite(length) || length < 0.0 || elevationAtStation == null) {
            return List.of();
        }
        int wholeBlocks = (int) Math.floor(length);
        List<Integer> result = new ArrayList<>(wholeBlocks + 2);
        for (int station = 0; station <= wholeBlocks; station++) {
            appendStepLimited(result, quantizeContinuous(elevationAtStation.applyAsDouble(station)));
        }
        if (Math.abs(length - wholeBlocks) > EPSILON) {
            appendStepLimited(result, quantizeContinuous(elevationAtStation.applyAsDouble(length)));
        }
        return List.copyOf(result);
    }

    private static void appendStepLimited(List<Integer> values, int desired) {
        if (values.isEmpty()) {
            values.add(desired);
            return;
        }
        int previous = values.getLast();
        values.add(Math.max(previous - 1, Math.min(previous + 1, desired)));
    }
}
