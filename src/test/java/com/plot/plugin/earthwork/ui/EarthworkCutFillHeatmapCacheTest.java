package com.plot.plugin.earthwork.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class EarthworkCutFillHeatmapCacheTest {

    @Test
    void reusesAggregationUntilPreviewOrGridChanges() {
        EarthworkCutFillHeatmapCache cache = new EarthworkCutFillHeatmapCache();
        DesignTerrainGrid grid = singleCellGrid(64);
        EarthworkGenerationResult preview = previewWithGrid(1L, "region-a", grid);

        var first = cache.resolve(preview, grid, 8, 6);
        var second = cache.resolve(preview, grid, 8, 6);
        assertSame(first, second);

        preview.designRevision = 2L;
        var afterRevision = cache.resolve(preview, grid, 8, 6);
        assertSame(afterRevision, cache.resolve(preview, grid, 8, 6));

        preview.sourceRegionId = "region-b";
        var afterRegion = cache.resolve(preview, grid, 8, 6);
        assertSame(afterRegion, cache.resolve(preview, grid, 8, 6));

        var afterSize = cache.resolve(preview, grid, 10, 6);
        assertSame(afterSize, cache.resolve(preview, grid, 10, 6));

        DesignTerrainGrid changed = singleCellGrid(65);
        preview.designTerrainGrid = changed;
        preview.earthworkGridFingerprint = DesignTerrainGrid.earthworkContentFingerprint(changed);
        var afterGrid = cache.resolve(preview, changed, 10, 6);
        assertSame(afterGrid, cache.resolve(preview, changed, 10, 6));
    }

    @Test
    void largeGridHitsCacheOnSecondResolveWithoutRehashingBoundGrid() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        for (int x = 0; x < 120; x++) {
            for (int z = 0; z < 120; z++) {
                DesignTerrainCell cell = new DesignTerrainCell(x, z, new Vec2d(x + 0.5, z + 0.5), 64);
                cell.setTargetY(64 + ((x + z) % 3) - 1);
                cell.setZoneId("pad");
                grid.put(x, z, cell);
            }
        }
        EarthworkGenerationResult preview = previewWithGrid(42L, "large", grid);
        long stamped = preview.earthworkGridFingerprint;
        EarthworkCutFillHeatmapCache cache = new EarthworkCutFillHeatmapCache();
        var first = cache.resolve(preview, grid, 32, 24);
        var second = cache.resolve(preview, grid, 32, 24);
        assertSame(first, second);
        assertEquals(stamped, preview.earthworkGridFingerprint);
        assertEquals(stamped, EarthworkCutFillHeatmapCache.resolveGridFingerprint(preview, grid));
    }

    private static EarthworkGenerationResult previewWithGrid(
            long designRevision, String regionId, DesignTerrainGrid grid) {
        EarthworkGenerationResult preview = new EarthworkGenerationResult();
        preview.designRevision = designRevision;
        preview.terrainRevision = 99L;
        preview.sourceRegionId = regionId;
        preview.designTerrainGrid = grid;
        preview.earthworkGridFingerprint = DesignTerrainGrid.earthworkContentFingerprint(grid);
        return preview;
    }

    private static DesignTerrainGrid singleCellGrid(int targetY) {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        DesignTerrainCell cell = new DesignTerrainCell(0, 0, new Vec2d(0.5, 0.5), 64);
        cell.setTargetY(targetY);
        cell.setZoneId("pad");
        grid.put(0, 0, cell);
        return grid;
    }
}
