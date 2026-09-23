package com.plot.plugin.building.generation.opening;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingGenerationContext.GridCell;
import com.plot.plugin.building.generation.massing.InnerOffsetDegradation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 外墙环带上的方块柱列，按轮廓弧长排序并去重（与 {@link com.plot.plugin.building.generation.stage.WallGenerationStage} 同格网）。
 */
public final class WallColumnRing {
    private WallColumnRing() {
    }

    public record WallColumn(int arcIndex, Vec2d center, int segmentIndex) {
    }

    /**
     * @param arcIndex 沿外轮廓的离散弧长步长（1 = 1 画布单位 ≈ 1 世界方块），用于窗间距计数
     */
    public static List<WallColumn> build(
            List<Vec2d> outerPoints,
            Polygon outerPolygon,
            Polygon innerPolygon,
            List<GridCell> outerCells) {
        if (outerPoints == null || outerPoints.size() < 3 || outerCells == null || outerCells.isEmpty()) {
            return List.of();
        }

        List<WallColumn> candidates = new ArrayList<>();
        for (GridCell cell : outerCells) {
            Vec2d center = cell.center();
            if (!InnerOffsetDegradation.isWallMassCell(outerPolygon, innerPolygon, center)) {
                continue;
            }
            int arcIndex = arcIndexOf(outerPoints, center);
            int segment = BuildingGeometryUtils.segmentIndexAtClosedDistance(outerPoints, arcIndex);
            candidates.add(new WallColumn(arcIndex, center, segment));
        }

        candidates.sort(Comparator.comparingInt(WallColumn::arcIndex));

        Set<Long> seenColumns = new LinkedHashSet<>();
        List<WallColumn> ring = new ArrayList<>();
        for (WallColumn column : candidates) {
            long key = packColumnKey(column.center());
            if (seenColumns.add(key)) {
                ring.add(column);
            }
        }
        return ring;
    }

    /** 画布格心 → 弧长步长索引（与墙格网 center 对齐）。 */
    static int arcIndexOf(List<Vec2d> outerPoints, Vec2d center) {
        double arc = arcLengthAtPoint(outerPoints, center);
        return (int) Math.round(arc);
    }

    public static double arcLengthAtPoint(List<Vec2d> outerPoints, Vec2d point) {
        double bestArc = 0.0;
        double bestDist = Double.POSITIVE_INFINITY;
        double accumulated = 0.0;
        int n = outerPoints.size();
        for (int i = 0; i < n; i++) {
            Vec2d start = outerPoints.get(i);
            Vec2d end = outerPoints.get((i + 1) % n);
            double segLen = start.distance(end);
            if (segLen < 1e-9) {
                continue;
            }
            Vec2d ab = end.subtract(start);
            double t = Math.max(0.0, Math.min(1.0, point.subtract(start).dot(ab) / ab.dot(ab)));
            Vec2d projection = start.add(ab.multiply(t));
            double dist = point.distance(projection);
            if (dist < bestDist) {
                bestDist = dist;
                bestArc = accumulated + t * segLen;
            }
            accumulated += segLen;
        }
        return bestArc;
    }

    private static long packColumnKey(Vec2d center) {
        long qx = Math.round(center.x * 1024.0);
        long qz = Math.round(center.y * 1024.0);
        return (qx << 32) ^ (qz & 0xffffffffL);
    }
}
