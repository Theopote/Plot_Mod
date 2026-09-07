package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.List;

/**
 * 电线杆位布局（纯函数）。
 */
public final class PowerPoleLayoutUtils {

    private PowerPoleLayoutUtils() {
    }

    /**
     * 沿路径计算立杆位置：起点、终点、转折顶点强制立杆；相邻强制点间距超过最大值时等距补插。
     */
    public static List<Vec2d> computePolePositions(
            List<Vec2d> pathPoints,
            double cornerAngleThreshold,
            double maxPoleSpacing) {
        if (pathPoints == null || pathPoints.isEmpty()) {
            return List.of();
        }
        if (pathPoints.size() == 1) {
            return List.of(pathPoints.getFirst().copy());
        }

        double maxSpacing = Math.max(0.1, maxPoleSpacing);
        List<Vec2d> mandatory = collectMandatoryPoints(pathPoints, cornerAngleThreshold);
        if (mandatory.isEmpty()) {
            return List.of();
        }

        List<Vec2d> result = new ArrayList<>();
        result.add(mandatory.getFirst().copy());
        for (int i = 0; i < mandatory.size() - 1; i++) {
            Vec2d from = mandatory.get(i);
            Vec2d to = mandatory.get(i + 1);
            appendInterpolatedPoles(result, from, to, maxSpacing);
        }
        return result;
    }

    private static List<Vec2d> collectMandatoryPoints(List<Vec2d> pathPoints, double cornerAngleThreshold) {
        List<Vec2d> mandatory = new ArrayList<>();
        mandatory.add(pathPoints.getFirst().copy());
        for (int i = 1; i < pathPoints.size() - 1; i++) {
            if (isCorner(pathPoints, i, cornerAngleThreshold)) {
                mandatory.add(pathPoints.get(i).copy());
            }
        }
        mandatory.add(pathPoints.getLast().copy());
        return mandatory;
    }

    public static boolean isCorner(List<Vec2d> pathPoints, int index, double cornerAngleThreshold) {
        Vec2d prev = pathPoints.get(index - 1);
        Vec2d current = pathPoints.get(index);
        Vec2d next = pathPoints.get(index + 1);
        Vec2d in = current.subtract(prev);
        Vec2d out = next.subtract(current);
        if (in.lengthSquared() < 1e-12 || out.lengthSquared() < 1e-12) {
            return true;
        }
        double dot = in.normalize().dot(out.normalize());
        double angleDeg = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
        return angleDeg > cornerAngleThreshold;
    }

    private static void appendInterpolatedPoles(
            List<Vec2d> result,
            Vec2d from,
            Vec2d to,
            double maxSpacing) {
        double distance = from.distance(to);
        if (distance <= maxSpacing) {
            result.add(to.copy());
            return;
        }
        int segments = (int) Math.ceil(distance / maxSpacing);
        for (int i = 1; i <= segments; i++) {
            double t = (double) i / segments;
            result.add(from.lerp(to, t));
        }
    }
}
