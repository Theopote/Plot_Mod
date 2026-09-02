package com.plot.plugin.road.vertical;

import com.plot.core.geometry.VoxelElevationDiscretizer;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * @deprecated 使用 {@link VoxelElevationDiscretizer}。
 */
@Deprecated
public final class VoxelGradeDiscretizer {
    private VoxelGradeDiscretizer() {
    }

    public static int quantizeContinuous(double elevation) {
        return VoxelElevationDiscretizer.quantizeContinuous(elevation);
    }

    public static int linearElevationAtRatio(int startY, int endY, double ratio) {
        return VoxelElevationDiscretizer.linearElevationAtRatio(startY, endY, ratio);
    }

    public static List<Integer> discretizeContinuousProfile(
            double length,
            DoubleUnaryOperator elevationAtStation) {
        return VoxelElevationDiscretizer.discretizeContinuousProfile(length, elevationAtStation);
    }
}
