package com.plot.core.geometry;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;

import java.util.List;

/** 画布几何在 Minecraft 世界空间中的距离与插值。 */
public final class WorldProjectionMath {
    private WorldProjectionMath() {
    }

    public static double pathWorldLength(ICoordinateService coordinates, List<Vec2d> pathPoints) {
        if (pathPoints == null || pathPoints.size() < 2 || coordinates == null) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            total += coordinates.projectedDistance(pathPoints.get(i), pathPoints.get(i + 1));
        }
        return total;
    }

    /**
     * 沿线段按世界距离（blocks）从 A 向 B 插值画布点。
     *
     * @param worldOffsetFromA 自 A 起的世界距离（blocks）
     */
    public static Vec2d canvasPointAtWorldOffsetOnSegment(
            ICoordinateService coordinates,
            Vec2d canvasA,
            Vec2d canvasB,
            double worldOffsetFromA) {
        if (coordinates == null || canvasA == null || canvasB == null) {
            return canvasA != null ? canvasA.copy() : new Vec2d(0, 0);
        }
        double segmentWorldLength = coordinates.projectedDistance(canvasA, canvasB);
        if (segmentWorldLength < 1e-9) {
            return canvasA.copy();
        }
        if (worldOffsetFromA <= 0.0) {
            return canvasA.copy();
        }
        if (worldOffsetFromA >= segmentWorldLength - 1e-6) {
            return canvasB.copy();
        }
        double t = worldOffsetFromA / segmentWorldLength;
        return canvasA.lerp(canvasB, t);
    }

    /** 沿折线按世界里程（自起点 blocks）取画布点。 */
    public static Vec2d canvasPointAtWorldStation(
            ICoordinateService coordinates,
            List<Vec2d> pathPoints,
            double worldStation) {
        if (pathPoints == null || pathPoints.isEmpty() || coordinates == null) {
            return new Vec2d(0, 0);
        }
        if (pathPoints.size() == 1) {
            return pathPoints.getFirst().copy();
        }
        double remaining = Math.max(0.0, worldStation);
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Vec2d a = pathPoints.get(i);
            Vec2d b = pathPoints.get(i + 1);
            double segLen = coordinates.projectedDistance(a, b);
            if (segLen < 1e-12) {
                continue;
            }
            if (remaining <= segLen + 1e-6) {
                return canvasPointAtWorldOffsetOnSegment(coordinates, a, b, remaining);
            }
            remaining -= segLen;
        }
        return pathPoints.getLast().copy();
    }

    /**
     * 估计沿给定方向「1 个 Minecraft 方块」对应多少画布坐标单位。
     * <p>
     * 块数参数在几何计算前应乘以本系数，再用于画布空间的偏移/采样。
     */
    public static double canvasUnitsPerWorldBlock(
            ICoordinateService coordinates,
            Vec2d origin,
            Vec2d direction) {
        if (coordinates == null || origin == null) {
            return 1.0;
        }
        Vec2d dir = direction != null && direction.lengthSquared() > 1e-12
            ? direction.normalize()
            : new Vec2d(0, 1);
        double probe = 1.0;
        double worldDist = coordinates.projectedDistance(origin, origin.add(dir.multiply(probe)));
        if (worldDist < 1e-4) {
            probe = 100.0;
            worldDist = coordinates.projectedDistance(origin, origin.add(dir.multiply(probe)));
        }
        if (worldDist < 1e-6) {
            return 1.0;
        }
        double units = probe / worldDist;
        return Math.max(0.05, Math.min(units, 500.0));
    }
}
