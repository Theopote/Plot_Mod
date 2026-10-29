package com.plot.plugin.earthwork.ui;

import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;

/**
 * ImGui 热力图栅格聚合缓存：避免每帧遍历完整设计网格。
 * <p>
 * 缓存键使用预览上预计算的 {@link EarthworkGenerationResult#earthworkGridFingerprint}，
 * 命中路径不再调用 {@link DesignTerrainGrid#earthworkContentFingerprint}。
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
            resolveGridFingerprint(preview, grid),
            cols,
            rows,
            extentsFingerprint(extents));
    }

    /**
     * 优先用预览上已缓存的指纹；仅当预览未绑定该网格或指纹尚未写入时才扫网格一次，
     * 并在绑定时回写，保证后续命中不再遍历。
     */
    static long resolveGridFingerprint(EarthworkGenerationResult preview, DesignTerrainGrid grid) {
        if (preview != null && preview.designTerrainGrid == grid) {
            if (preview.earthworkGridFingerprint == 0L && grid != null && grid.cellCount() > 0) {
                preview.earthworkGridFingerprint = DesignTerrainGrid.earthworkContentFingerprint(grid);
            }
            return preview.earthworkGridFingerprint;
        }
        return DesignTerrainGrid.earthworkContentFingerprint(grid);
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
