package com.plot.plugin.powerline.path;

import com.plot.api.geometry.Vec2d;

import java.util.List;

/** 闭合线路几何校验与里程归一化。 */
public final class ClosedPathGeometry {
    /** 塔位三角形面积下限（画布坐标 shoelace）。 */
    public static final double AREA_EPSILON = 1e-2;

    private ClosedPathGeometry() {
    }

    public static double normalizeStation(
            PowerLineSourcePath path,
            double worldStationBlocks,
            double perimeterBlocks) {
        if (path == null || !path.isClosed() || perimeterBlocks <= 1e-12) {
            return Math.max(0.0, Math.min(perimeterBlocks, worldStationBlocks));
        }
        return ClosedPathStationMath.normalize(worldStationBlocks, perimeterBlocks);
    }

    public static double signedArea(List<Vec2d> points) {
        if (points == null || points.size() < 3) {
            return 0.0;
        }
        double area = 0.0;
        for (int i = 0; i < points.size(); i++) {
            Vec2d current = points.get(i);
            Vec2d next = points.get((i + 1) % points.size());
            area += current.x * next.y - next.x * current.y;
        }
        return area * 0.5;
    }

    public static boolean isValidTowerLoop(List<Vec2d> towerPoints) {
        if (towerPoints == null || towerPoints.size() < 3) {
            return false;
        }
        return Math.abs(signedArea(towerPoints)) > AREA_EPSILON;
    }

    /** 沿闭合路径的最短世界里程距离（blocks）；开放路径退化为 {@code |a-b|}。 */
    public static double stationDistance(double stationA, double stationB, double perimeterBlocks) {
        return ClosedPathStationMath.distance(stationA, stationB, perimeterBlocks, true);
    }

    public static boolean stationsWithinTolerance(
            double stationA,
            double stationB,
            double perimeterBlocks,
            double toleranceBlocks) {
        return stationDistance(stationA, stationB, perimeterBlocks) <= toleranceBlocks;
    }
}
