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
            int rows) {
    }

    private Key key;
    private EarthworkCutFillHeatmap.Bucket[][] buckets;

    EarthworkCutFillHeatmap.Bucket[][] resolve(
            EarthworkGenerationResult preview,
            DesignTerrainGrid grid,
            int cols,
            int rows) {
        if (grid == null || grid.cellCount() == 0) {
            clear();
            return null;
        }
        Key candidate = keyFor(preview, grid, cols, rows);
        if (candidate.equals(key) && buckets != null) {
            return buckets;
        }
        buckets = EarthworkCutFillHeatmap.aggregate(grid, cols, rows);
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
            int rows) {
        long designRevision = preview != null ? preview.designRevision : 0L;
        long terrainRevision = preview != null ? preview.terrainRevision : 0L;
        String regionId = preview != null && preview.sourceRegionId != null ? preview.sourceRegionId : "";
        return new Key(
            designRevision,
            terrainRevision,
            regionId,
            DesignTerrainGrid.earthworkContentFingerprint(grid),
            cols,
            rows);
    }
}
