package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;

/**
 * 闭环道路的剖面开口点（Profile Seam）。不物理剪断几何，仅定义 canonical 桩号 0/L 的物理位置。
 */
public record RoadLoopSeam(
        Vec2d position,
        String segmentHintId,
        Double localFraction) {

    public RoadLoopSeam {
        if (position == null) {
            throw new IllegalArgumentException("loop seam position required");
        }
        position = position.copy();
        if (localFraction != null) {
            localFraction = Math.max(0.0, Math.min(1.0, localFraction));
        }
    }

    public static RoadLoopSeam at(Vec2d position) {
        return new RoadLoopSeam(position, null, null);
    }

    public static RoadLoopSeam onSegment(Vec2d position, String segmentId, double localFraction) {
        return new RoadLoopSeam(position, segmentId, localFraction);
    }
}
