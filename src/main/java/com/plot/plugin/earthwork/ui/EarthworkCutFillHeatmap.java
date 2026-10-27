package com.plot.plugin.earthwork.ui;

import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;

/** 挖填热力图：屏幕固定栅格聚合，避免按格点列表步长跳过。 */
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

    private EarthworkCutFillHeatmap() {
    }

    static Bucket[][] aggregate(DesignTerrainGrid grid, int gridWidth, int gridHeight) {
        int cols = Math.max(1, gridWidth);
        int rows = Math.max(1, gridHeight);
        Bucket[][] buckets = new Bucket[cols][rows];
        for (int x = 0; x < cols; x++) {
            for (int z = 0; z < rows; z++) {
                buckets[x][z] = new Bucket(0, 0, 0);
            }
        }
        if (grid == null || grid.cellCount() == 0) {
            return buckets;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (cell == null || !cell.participatesInEarthwork()) {
                continue;
            }
            minX = Math.min(minX, cell.worldX());
            maxX = Math.max(maxX, cell.worldX());
            minZ = Math.min(minZ, cell.worldZ());
            maxZ = Math.max(maxZ, cell.worldZ());
        }
        if (minX == Integer.MAX_VALUE) {
            return buckets;
        }
        double spanX = Math.max(1, maxX - minX + 1);
        double spanZ = Math.max(1, maxZ - minZ + 1);
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (cell == null || !cell.participatesInEarthwork()) {
                continue;
            }
            int bx = (int) Math.floor((cell.worldX() - minX) / spanX * cols);
            int bz = (int) Math.floor((cell.worldZ() - minZ) / spanZ * rows);
            bx = Math.min(cols - 1, Math.max(0, bx));
            bz = Math.min(rows - 1, Math.max(0, bz));
            Bucket prev = buckets[bx][bz];
            int delta = cell.deltaY();
            if (delta > 0) {
                buckets[bx][bz] = new Bucket(prev.cutCount, prev.fillCount + 1, prev.unchangedCount);
            } else if (delta < 0) {
                buckets[bx][bz] = new Bucket(prev.cutCount + 1, prev.fillCount, prev.unchangedCount);
            } else {
                buckets[bx][bz] = new Bucket(prev.cutCount, prev.fillCount, prev.unchangedCount + 1);
            }
        }
        return buckets;
    }
}
