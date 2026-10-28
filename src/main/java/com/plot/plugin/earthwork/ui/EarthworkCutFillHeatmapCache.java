package com.plot.plugin.earthwork.ui;

import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;

/**
 * ImGui 热力图栅格聚合缓存：避免每帧遍历完整设计网格。
 */
final class EarthworkCutFillHeatmapCache {

    private record Key(
            long designRevision,
            long terrainRevision,
            String sourceRegionId,
            long gridFingerprint,
            int cols,
            int rows,
            long extentsFingerprint) {
    }

    private Key key;
    private EarthworkCutFillHeatmap.Bucket[][] buckets;

    EarthworkCutFillHeatmap.Bucket[][] resolve(
            EarthworkGenerationResult preview,
            DesignTerrainGrid grid,
            int cols,
            int rows) {
        return resolve(preview, grid, cols, rows, EarthworkCutFillHeatmap.canvasExtents(grid));
    }

    EarthworkCutFillHeatmap.Bucket[][] resolve(
            EarthworkGenerationResult preview,
            DesignTerrainGrid grid,
            int cols,
            int rows,
            EarthworkCutFillHeatmap.CanvasExtents extents) {
        if (grid == null || grid.cellCount() == 0 || extents == null) {
            clear();
            return null;
        }
        Key candidate = keyFor(preview, grid, cols, rows, extents);
        if (candidate.equals(key) && buckets != null) {
            return buckets;
        }
        buckets = EarthworkCutFillHeatmap.aggregate(grid, cols, rows, extents);
        key = candidate;
        return buckets;
    }

    void clear() {
        key = null;
        buckets = null;
    }

    static Key keyFor(
            EarthworkGenerationResult preview,
            DesignTerrainGrid grid,
            int cols,
            int rows,
            EarthworkCutFillHeatmap.CanvasExtents extents) {
        long designRevision = preview != null ? preview.designRevision : 0L;
        long terrainRevision = preview != null ? preview.terrainRevision : 0L;
        String regionId = preview != null && preview.sourceRegionId != null ? preview.sourceRegionId : "";
        return new Key(
            designRevision,
            terrainRevision,
            regionId,
            DesignTerrainGrid.earthworkContentFingerprint(grid),
            cols,
            rows,
            extentsFingerprint(extents));
    }

    private static long extentsFingerprint(EarthworkCutFillHeatmap.CanvasExtents extents) {
        if (extents == null) {
            return 0L;
        }
        long hash = Double.hashCode(extents.minX());
        hash = 31L * hash + Double.hashCode(extents.maxX());
        hash = 31L * hash + Double.hashCode(extents.minY());
        hash = 31L * hash + Double.hashCode(extents.maxY());
        return hash;
    }
}
