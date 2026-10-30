package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 铁路简易道岔几何辅助：选出对向贯通直股，并规划静态视觉铺轨（非信号/调度系统）。
 * <p>
 * Minecraft 原版无法在同一格实现任意三向道岔；本类保证<strong>直股路径</strong>连续铺轨，
 * 支线以延续轨接入广场，装饰用动力轨/探测轨不覆盖直股连通骨架。
 */
public final class RoadRailwayTurnout {
    /** 视为对向贯通的最大点积（约 ≥110° 夹角）；更钝则不强制选直股。 */
    public static final double THROUGH_PAIR_MAX_DOT = -0.35;

    /** 进口延续轨（进广场） */
    public static final int PRIORITY_APPROACH_RAIL = 10;
    /** 直股连通骨架（普通铁轨）；岔心保留此优先级，不被装饰轨覆盖 */
    public static final int PRIORITY_THROUGH_CONNECT = 14;
    /** 直股装饰动力轨（离开岔心与探测标记格；高于骨架以便可见） */
    public static final int PRIORITY_POWERED_DECOR = 16;
    /** 探测轨标记（岔心外侧约 1 格；最高，避免被动力轨 round 同格覆盖） */
    public static final int PRIORITY_DETECTOR_MARK = 17;

    private RoadRailwayTurnout() {
    }

    /**
     * @param outwards 各铁路进口的外向单位向量（离开节点）
     * @return 直股两进口下标；不足两进口或没有足够对向关系时 empty
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
                if (dot < bestDot) {
                    bestDot = dot;
                    bestI = i;
                    bestJ = j;
                }
            }
        }
        if (bestI < 0 || bestJ < 0 || bestDot > THROUGH_PAIR_MAX_DOT) {
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

    /**
     * 直股装饰动力轨：在强化带内，但离开岔心至少 1.75 格，
     * 避免 round 后与探测标记格（约 1 格处）冲突。
     */
    public static boolean inPoweredDecorBand(Vec2d point, Vec2d center, double junctionRadius) {
        if (!inThroughPowerBand(point, center, junctionRadius)) {
            return false;
        }
        return point.distanceSquared(center) >= 1.75 * 1.75;
    }

    /**
     * 探测轨标记位置：沿直股方向离开中心约 1 格（世界/画布单位），不占岔心汇合格。
     */
    public static Optional<Vec2d> detectorMarkPosition(
            Vec2d center,
            Vec2d throughOutwardA,
            Vec2d throughOutwardB) {
        if (center == null) {
            return Optional.empty();
        }
        Vec2d dir = null;
        if (throughOutwardA != null && throughOutwardA.lengthSquared() > 1e-12) {
            dir = throughOutwardA.normalize();
        } else if (throughOutwardB != null && throughOutwardB.lengthSquared() > 1e-12) {
            dir = throughOutwardB.normalize().multiply(-1.0);
        }
        if (dir == null) {
            return Optional.empty();
        }
        return Optional.of(center.add(dir.multiply(1.0)));
    }

    /**
     * 密化直股折线（两进口中心线经岔心），供连通骨架铺轨。
     */
    public static List<Vec2d> densifyThroughPath(
            List<Vec2d> armAInward,
            List<Vec2d> armBInward,
            Vec2d center,
            double step) {
        List<Vec2d> path = new ArrayList<>();
        if (armAInward != null) {
            path.addAll(armAInward);
        }
        if (center != null
                && (path.isEmpty() || path.getLast().distanceSquared(center) > 1e-8)) {
            path.add(center);
        }
        if (armBInward != null && !armBInward.isEmpty()) {
            // armBInward 朝向中心；反向后从中心走出
            for (int i = armBInward.size() - 1; i >= 0; i--) {
                Vec2d p = armBInward.get(i);
                if (path.isEmpty() || path.getLast().distanceSquared(p) > 1e-8) {
                    path.add(p);
                }
            }
        }
        return densifyPolyline(path, step);
    }

    public static List<Vec2d> densifyPolyline(List<Vec2d> points, double step) {
        if (points == null || points.size() < 2 || step <= 1e-9) {
            return points != null ? List.copyOf(points) : List.of();
        }
        List<Vec2d> dense = new ArrayList<>();
        dense.add(points.getFirst());
        for (int i = 1; i < points.size(); i++) {
            Vec2d prev = points.get(i - 1);
            Vec2d curr = points.get(i);
            double len = prev.distance(curr);
            if (len < 1e-9) {
                continue;
            }
            int segments = Math.max(1, (int) Math.ceil(len / step));
            for (int s = 1; s <= segments; s++) {
                dense.add(prev.lerp(curr, (double) s / segments));
            }
        }
        return dense;
    }

    /**
     * 校验直股密化点列在去重到整数格后是否形成 4-邻接连通链（静态连通示意）。
     */
    public static boolean isGridConnectedChain(List<Vec2d> densifiedPoints) {
        if (densifiedPoints == null || densifiedPoints.size() < 2) {
            return false;
        }
        List<long[]> cells = new ArrayList<>();
        long prevKey = Long.MIN_VALUE;
        for (Vec2d point : densifiedPoints) {
            if (point == null) {
                continue;
            }
            int x = (int) Math.round(point.x);
            int y = (int) Math.round(point.y);
            long key = (((long) x) << 32) ^ (y & 0xffffffffL);
            if (key == prevKey) {
                continue;
            }
            cells.add(new long[] {x, y});
            prevKey = key;
        }
        if (cells.size() < 2) {
            return false;
        }
        for (int i = 1; i < cells.size(); i++) {
            long dx = Math.abs(cells.get(i)[0] - cells.get(i - 1)[0]);
            long dy = Math.abs(cells.get(i)[1] - cells.get(i - 1)[1]);
            if (dx + dy != 1 && !(dx == 0 && dy == 0)) {
                // 允许对角一步（斜线采样），否则必须四邻接
                if (dx > 1 || dy > 1) {
                    return false;
                }
            }
        }
        return true;
    }
}
