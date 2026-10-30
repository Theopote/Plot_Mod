package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;

import java.util.List;
import java.util.Optional;

/**
 * 铁路简易道岔几何辅助：从多进口中选出最接近对向贯通的直股。
 */
public final class RoadRailwayTurnout {
    private RoadRailwayTurnout() {
    }

    /**
     * @param outwards 各铁路进口的外向单位向量（离开节点）
     * @return 直股两进口下标；不足两进口时 empty
     */
    public static Optional<int[]> resolveThroughPairIndices(List<Vec2d> outwards) {
        if (outwards == null || outwards.size() < 2) {
            return Optional.empty();
        }
        int bestI = -1;
        int bestJ = -1;
        double bestDot = Double.POSITIVE_INFINITY;
        for (int i = 0; i < outwards.size(); i++) {
            Vec2d a = outwards.get(i);
            if (a == null || a.lengthSquared() < 1e-12) {
                continue;
            }
            Vec2d au = a.normalize();
            for (int j = i + 1; j < outwards.size(); j++) {
                Vec2d b = outwards.get(j);
                if (b == null || b.lengthSquared() < 1e-12) {
                    continue;
                }
                double dot = au.x * b.normalize().x + au.y * b.normalize().y;
                // 对向贯通：点积最负
                if (dot < bestDot) {
                    bestDot = dot;
                    bestI = i;
                    bestJ = j;
                }
            }
        }
        if (bestI < 0 || bestJ < 0) {
            return Optional.empty();
        }
        return Optional.of(new int[] {bestI, bestJ});
    }

    /** 点是否落在道岔直股强化区（靠近节点中心）。 */
    public static boolean inThroughPowerBand(Vec2d point, Vec2d center, double junctionRadius) {
        if (point == null || center == null) {
            return false;
        }
        double band = Math.max(1.5, junctionRadius * 0.65);
        return point.distanceSquared(center) <= band * band;
    }
}
