package com.plot.plugin.earthwork.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;

import java.util.Collection;
import java.util.List;

/**
 * 挖填热力图：按<strong>画布</strong>格心坐标聚合，使面板示意与画布俯视布局同比例、同方位。
 * <p>
 * 仅统计已归属分区的格点；框体优先取各分区外轮廓的画布包围盒，避免把场地空白也铺成「一整块」。
 */
final class EarthworkCutFillHeatmap {

    enum Dominant {
        CUT, FILL, UNCHANGED
    }

    record Bucket(int cutCount, int fillCount, int unchangedCount) {
        Dominant dominant() {
            if (cutCount >= fillCount && cutCount >= unchangedCount && cutCount > 0) {
                return Dominant.CUT;
            }
            if (fillCount >= unchangedCount && fillCount > 0) {
                return Dominant.FILL;
            }
            return Dominant.UNCHANGED;
        }
    }

    /** 参与挖填格点 / 分区轮廓的画布包围盒，用于面板示意比例与落点。 */
    record CanvasExtents(double minX, double maxX, double minY, double maxY) {
        double spanX() {
            return Math.max(1e-6, maxX - minX);
        }

        double spanY() {
            return Math.max(1e-6, maxY - minY);
        }

        /** 宽/高（画布 X / Y），与画布俯视一致。 */
        double aspectWidthOverHeight() {
            return spanX() / spanY();
        }
    }

    private EarthworkCutFillHeatmap() {
    }

    /**
     * 优先用分区外轮廓定框（画布上有几个区就框住几个）；无轮廓时回退到已归属分区的格心。
     */
    static CanvasExtents canvasExtents(
            DesignTerrainGrid grid,
            Collection<? extends List<Vec2d>> zoneOutlines) {
        CanvasExtents fromOutlines = canvasExtentsFromOutlines(zoneOutlines);
        if (fromOutlines != null) {
            return fromOutlines;
        }
        return canvasExtents(grid);
    }

    static CanvasExtents canvasExtentsFromOutlines(Collection<? extends List<Vec2d>> zoneOutlines) {
        if (zoneOutlines == null || zoneOutlines.isEmpty()) {
            return null;
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        boolean any = false;
        for (List<Vec2d> outline : zoneOutlines) {
            if (outline == null || outline.size() < 3) {
                continue;
            }
            for (Vec2d point : outline) {
                if (point == null) {
                    continue;
                }
                minX = Math.min(minX, point.x);
                maxX = Math.max(maxX, point.x);
                minY = Math.min(minY, point.y);
                maxY = Math.max(maxY, point.y);
                any = true;
            }
        }
        if (!any) {
            return null;
        }
        return normalizeExtents(minX, maxX, minY, maxY);
    }

    static CanvasExtents canvasExtents(DesignTerrainGrid grid) {
        if (grid == null || grid.cellCount() == 0) {
            return null;
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (!isHeatmapCell(cell)) {
                continue;
            }
            Vec2d center = cell.center();
            minX = Math.min(minX, center.x);
            maxX = Math.max(maxX, center.x);
            minY = Math.min(minY, center.y);
            maxY = Math.max(maxY, center.y);
        }
        if (!Double.isFinite(minX)) {
            return null;
        }
        return normalizeExtents(minX, maxX, minY, maxY);
    }

    static Bucket[][] aggregate(DesignTerrainGrid grid, int gridWidth, int gridHeight) {
        return aggregate(grid, gridWidth, gridHeight, canvasExtents(grid));
    }

    static Bucket[][] aggregate(
            DesignTerrainGrid grid,
            int gridWidth,
            int gridHeight,
            CanvasExtents extents) {
        int cols = Math.max(1, gridWidth);
        int rows = Math.max(1, gridHeight);
        Bucket[][] buckets = new Bucket[cols][rows];
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                buckets[x][y] = new Bucket(0, 0, 0);
            }
        }
        if (grid == null || extents == null) {
            return buckets;
        }
        double minX = extents.minX();
        double minY = extents.minY();
        double spanX = extents.spanX();
        double spanY = extents.spanY();
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (!isHeatmapCell(cell)) {
                continue;
            }
            Vec2d center = cell.center();
            int bx = (int) Math.floor((center.x - minX) / spanX * cols);
            int by = (int) Math.floor((center.y - minY) / spanY * rows);
            bx = Math.clamp(bx, 0, cols - 1);
            by = Math.clamp(by, 0, rows - 1);
            Bucket prev = buckets[bx][by];
            int delta = cell.deltaY();
            if (delta > 0) {
                buckets[bx][by] = new Bucket(prev.cutCount(), prev.fillCount() + 1, prev.unchangedCount());
            } else if (delta < 0) {
                buckets[bx][by] = new Bucket(prev.cutCount() + 1, prev.fillCount(), prev.unchangedCount());
            } else {
                buckets[bx][by] = new Bucket(prev.cutCount(), prev.fillCount(), prev.unchangedCount() + 1);
            }
        }
        return buckets;
    }

    /** 已归属分区且参与挖填的格点才进入平面示意（场地空白不铺色）。 */
    static boolean isHeatmapCell(DesignTerrainCell cell) {
        if (cell == null || !cell.participatesInEarthwork()) {
            return false;
        }
        String zoneId = cell.zoneId();
        return zoneId != null && !zoneId.isBlank() && cell.center() != null;
    }

    private static CanvasExtents normalizeExtents(double minX, double maxX, double minY, double maxY) {
        if (maxX - minX < 1e-6) {
            maxX = minX + 1.0;
        }
        if (maxY - minY < 1e-6) {
            maxY = minY + 1.0;
        }
        return new CanvasExtents(minX, maxX, minY, maxY);
    }
}
