package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.geometry.polygon.PolygonNormalizer;
import com.plot.core.geometry.polygon.PolygonUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 建筑轮廓认领前安全修复：Normalize → Repair →（再由 {@link BuildingFootprintValidator} 校验）。
 * <p>
 * 自动处理：连续重复点、首尾闭合重复、共线冗余点、极短边、绕序统一（CCW）。
 * 不尝试修复：自交、零面积、严重退化 —— 由校验阶段硬拒绝。
 */
public final class BuildingFootprintRepair {
    /** GIS/CAD 导入常见的微短边阈值（画布坐标单位）。 */
    public static final double MIN_EDGE_LENGTH = 1e-3;

    private BuildingFootprintRepair() {
    }

    public record RepairResult(List<Vec2d> points, boolean repaired) {
        public RepairResult {
            points = points == null ? List.of() : List.copyOf(points);
        }
    }

    /**
     * 对原始轮廓点做安全修复；若无法保留 ≥3 个有效顶点则返回空列表。
     */
    public static RepairResult repair(List<Vec2d> raw) {
        if (raw == null || raw.isEmpty()) {
            return new RepairResult(List.of(), false);
        }
        List<Vec2d> snapshot = PolygonRegionUtils.copyPoints(raw);
        List<Vec2d> working = PolygonNormalizer.normalizeOutline(raw);
        boolean repaired = working.size() != countFiniteVertices(snapshot)
            || !vertexSequenceEquals(snapshot, working);

        if (working.size() < 3) {
            return new RepairResult(working, repaired);
        }

        List<Vec2d> withoutShortEdges = removeShortEdges(working, MIN_EDGE_LENGTH);
        if (withoutShortEdges.size() != working.size()) {
            repaired = true;
            working = withoutShortEdges;
        }

        List<Vec2d> decollinear = PolygonNormalizer.removeCollinearVertices(
            working, PolygonUtils.DEFAULT_EPSILON);
        if (decollinear.size() != working.size()) {
            repaired = true;
            working = decollinear;
        }

        if (working.size() < 3) {
            return new RepairResult(List.of(), true);
        }

        List<Vec2d> ccw = PolygonNormalizer.normalizeWinding(
            working, PolygonUtils.Winding.COUNTER_CLOCKWISE);
        if (!vertexSequenceEquals(working, ccw)) {
            repaired = true;
            working = ccw;
        }

        return new RepairResult(working.size() >= 3 ? working : List.of(), repaired);
    }

    static List<Vec2d> removeShortEdges(List<Vec2d> points, double minEdgeLength) {
        if (points == null || points.size() < 3 || minEdgeLength <= 0) {
            return points == null ? List.of() : PolygonRegionUtils.copyPoints(points);
        }
        List<Vec2d> working = PolygonRegionUtils.copyPoints(points);
        boolean changed;
        do {
            changed = false;
            if (working.size() < 3) {
                break;
            }
            int n = working.size();
            for (int i = 0; i < n && working.size() >= 3; i++) {
                int next = (i + 1) % working.size();
                if (working.get(i).distance(working.get(next)) < minEdgeLength) {
                    working.remove(next);
                    changed = true;
                    break;
                }
            }
        } while (changed);
        return working.size() >= 3 ? working : List.of();
    }

    private static int countFiniteVertices(List<Vec2d> points) {
        int count = 0;
        for (Vec2d point : points) {
            if (point != null && PolygonUtils.isFinite(point)) {
                count++;
            }
        }
        return count;
    }

    private static boolean vertexSequenceEquals(List<Vec2d> left, List<Vec2d> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (left.get(i).distance(right.get(i)) > PolygonUtils.CLOSE_RING_EPSILON) {
                return false;
            }
        }
        return true;
    }
}
