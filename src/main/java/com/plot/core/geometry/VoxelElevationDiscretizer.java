package com.plot.core.geometry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * 将连续设计高程离散为 Minecraft 方块级台阶（道路纵断面、土方坡面等共用）。
 */
public final class VoxelElevationDiscretizer {
    private static final double EPSILON = 1e-9;

    private VoxelElevationDiscretizer() {
    }

    public static int quantizeContinuous(double elevation) {
        if (!Double.isFinite(elevation)) {
            return 0;
        }
        return (int) Math.round(elevation);
    }

    /** Bresenham 式线性分配；ratio 限制在 [0, 1]。 */
    public static int linearElevationAtRatio(int startY, int endY, double ratio) {
        double clamped = Math.max(0.0, Math.min(1.0, ratio));
        int delta = endY - startY;
        int steps = (int) Math.floor(clamped * Math.abs(delta) + EPSILON);
        return startY + Integer.signum(delta) * steps;
    }

    /**
     * 沿一维参数 station 采样连续高程，每格步进不超过 1 方块。
     * {@code elevations[i]} 对应 station = i。
     */
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

    /** 从已离散 profile 查询 station 处高程（station 0 对应 {@code elevations[0]}）。 */
    public static int elevationAtStation(double station, int[] elevations) {
        if (elevations == null || elevations.length == 0) {
            return 0;
        }
        if (!Double.isFinite(station) || station <= EPSILON) {
            return elevations[0];
        }
        int index = (int) Math.floor(station + EPSILON);
        if (index >= elevations.length - 1) {
            return elevations[elevations.length - 1];
        }
        return elevations[index];
    }

    /**
     * 沿梯度方向从锚点出发，将线性坡面离散为 profile。
     *
     * @param anchorElevation 锚点连续高程
     * @param gradientMagnitude 每沿单位梯度方向 1 格的高程变化量
     * @param minProjection 相对锚点的最小投影（可为负）
     * @param maxProjection 相对锚点的最大投影
     */
    public static int[] discretizeLinearSlope(
            double anchorElevation,
            double gradientMagnitude,
            double minProjection,
            double maxProjection) {
        if (!Double.isFinite(minProjection) || !Double.isFinite(maxProjection)) {
            return new int[0];
        }
        double length = maxProjection - minProjection;
        if (length < 0.0) {
            double swap = minProjection;
            minProjection = maxProjection;
            maxProjection = swap;
            length = maxProjection - minProjection;
        }
        final double origin = minProjection;
        List<Integer> values = discretizeContinuousProfile(
            length,
            station -> anchorElevation + (origin + station) * gradientMagnitude);
        return values.stream().mapToInt(Integer::intValue).toArray();
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
