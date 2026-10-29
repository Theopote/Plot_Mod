package com.plot.plugin.earthwork.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkCutFillHeatmapTest {

    @Test
    void canvasExtentsFollowCanvasCentersNotWorldBlocks() {
        // 世界方块呈竖长 AABB，画布格心却是横宽布局 → 示意必须跟画布
        DesignTerrainGrid grid = new DesignTerrainGrid();
        grid.put(0, 0, cellAtWorld(0, 0, new Vec2d(0.5, 0.5), 0));
        grid.put(0, 20, cellAtWorld(0, 20, new Vec2d(9.5, 0.5), 0));
        grid.put(1, 0, cellAtWorld(1, 0, new Vec2d(0.5, 4.5), 0));
        grid.put(1, 20, cellAtWorld(1, 20, new Vec2d(9.5, 4.5), 0));

        EarthworkCutFillHeatmap.CanvasExtents extents = EarthworkCutFillHeatmap.canvasExtents(grid);
        assertNotNull(extents);
        // 画布 X:0.5..9.5 / Y:0.5..4.5 → 9/4，而非世界竖长 AABB
        assertEquals(9.0 / 4.0, extents.aspectWidthOverHeight(), 1e-6);
    }

    @Test
    void aggregatePlacesLeftCanvasCellInLeftColumn() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        // 左侧挖、右侧填（画布 X）；同一画布 Y
        grid.put(10, 50, cellAtWorld(10, 50, new Vec2d(1.0, 5.0), -2));
        grid.put(90, 50, cellAtWorld(90, 50, new Vec2d(9.0, 5.0), 2));

        EarthworkCutFillHeatmap.Bucket[][] buckets = EarthworkCutFillHeatmap.aggregate(grid, 8, 4);
        assertTrue(buckets[0][0].cutCount() > 0);
        assertTrue(buckets[7][0].fillCount() > 0);
    }

    @Test
    void displayMarginPadsCoreExtentsWithoutPainting() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        grid.put(2, 2, cellAtWorld(2, 2, new Vec2d(2.5, 2.5), -1));

        EarthworkCutFillHeatmap.CanvasExtents core = EarthworkCutFillHeatmap.canvasExtents(grid);
        assertNotNull(core);
        EarthworkCutFillHeatmap.CanvasExtents padded = EarthworkCutFillHeatmap.withDisplayMargin(core);
        assertNotNull(padded);
        assertTrue(padded.minX() < core.minX());
        assertTrue(padded.maxX() > core.maxX());
        assertTrue(padded.minY() < core.minY());
        assertTrue(padded.maxY() > core.maxY());
        assertTrue(
            padded.spanX() - core.spanX()
                >= 2.0 * EarthworkCutFillHeatmap.DISPLAY_MARGIN_MIN - 1e-6);

        EarthworkCutFillHeatmap.Bucket[][] buckets =
            EarthworkCutFillHeatmap.aggregate(grid, 16, 16, padded);
        int painted = 0;
        for (EarthworkCutFillHeatmap.Bucket[] col : buckets) {
            for (EarthworkCutFillHeatmap.Bucket bucket : col) {
                painted += bucket.cutCount() + bucket.fillCount() + bucket.unchangedCount();
            }
        }
        assertEquals(1, painted);
    }

    @Test
    void unassignedSitePaddingDoesNotExpandExtentsOrPaintBuckets() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        // 分区格：左上挖
        grid.put(2, 2, cellAtWorld(2, 2, new Vec2d(2.5, 2.5), -1));
        // 场地空白（无 zoneId）：若计入会把框拉到右下并铺绿
        DesignTerrainCell padding = new DesignTerrainCell(20, 20, new Vec2d(20.5, 20.5), 64);
        padding.setTargetY(64);
        grid.put(20, 20, padding);

        EarthworkCutFillHeatmap.CanvasExtents extents = EarthworkCutFillHeatmap.canvasExtents(grid);
        assertNotNull(extents);
        assertTrue(extents.maxX() < 10.0);
        assertTrue(extents.maxY() < 10.0);

        EarthworkCutFillHeatmap.Bucket[][] buckets = EarthworkCutFillHeatmap.aggregate(grid, 8, 8);
        int painted = 0;
        for (EarthworkCutFillHeatmap.Bucket[] col : buckets) {
            for (EarthworkCutFillHeatmap.Bucket bucket : col) {
                painted += bucket.cutCount() + bucket.fillCount() + bucket.unchangedCount();
            }
        }
        assertEquals(1, painted);
    }

    @Test
    void twoSeparatedZonesKeepRelativeCanvasPositions() {
        // 画布：圆区在上（小 Y）、方区在左下（大 Y、小 X）— 与玩家俯视一致
        DesignTerrainGrid grid = new DesignTerrainGrid();
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                grid.put(10 + i, 10 + j, cellAtWorld(
                    10 + i, 10 + j, new Vec2d(5.0 + i, 1.0 + j), -1));
            }
        }
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                grid.put(30 + i, 30 + j, cellAtWorld(
                    30 + i, 30 + j, new Vec2d(1.0 + i, 8.0 + j), 2));
            }
        }

        List<List<Vec2d>> outlines = List.of(
            List.of(new Vec2d(5, 1), new Vec2d(10, 1), new Vec2d(10, 6), new Vec2d(5, 6)),
            List.of(new Vec2d(1, 8), new Vec2d(5, 8), new Vec2d(5, 12), new Vec2d(1, 12)));
        EarthworkCutFillHeatmap.CanvasExtents extents =
            EarthworkCutFillHeatmap.canvasExtents(grid, outlines);
        assertNotNull(extents);
        // 框住上下两区 → 竖向跨度更大
        assertTrue(extents.spanY() > extents.spanX());

        EarthworkCutFillHeatmap.Bucket[][] buckets =
            EarthworkCutFillHeatmap.aggregate(grid, 12, 16, extents);

        // 上区（挖）应落在较小 row；下区（填）应落在较大 row，且偏左列
        int upperCutRow = firstRowWith(buckets, b -> b.cutCount() > 0);
        int lowerFillRow = firstRowWith(buckets, b -> b.fillCount() > 0);
        int lowerFillCol = firstColWith(buckets, b -> b.fillCount() > 0);
        int upperCutCol = firstColWith(buckets, b -> b.cutCount() > 0);
        assertTrue(upperCutRow >= 0 && lowerFillRow >= 0);
        assertTrue(upperCutRow < lowerFillRow, "upper zone must map above lower zone");
        assertTrue(lowerFillCol < upperCutCol, "lower-left square must sit left of upper circle");
    }

    @Test
    void aggregationIsOrderIndependent() {
        DesignTerrainGrid gridA = gridWithCells(
            cell(0, 0, -1),
            cell(1, 0, 1),
            cell(0, 1, 0));
        DesignTerrainGrid gridB = gridWithCells(
            cell(0, 1, 0),
            cell(1, 0, 1),
            cell(0, 0, -1));

        EarthworkCutFillHeatmap.Bucket[][] a = EarthworkCutFillHeatmap.aggregate(gridA, 4, 4);
        EarthworkCutFillHeatmap.Bucket[][] b = EarthworkCutFillHeatmap.aggregate(gridB, 4, 4);

        assertBucketMatricesEqual(a, b);
    }

    private static int firstRowWith(
            EarthworkCutFillHeatmap.Bucket[][] buckets,
            java.util.function.Predicate<EarthworkCutFillHeatmap.Bucket> pred) {
        for (int y = 0; y < buckets[0].length; y++) {
            for (EarthworkCutFillHeatmap.Bucket[] bucket : buckets) {
                if (pred.test(bucket[y])) {
                    return y;
                }
            }
        }
        return -1;
    }

    private static int firstColWith(
            EarthworkCutFillHeatmap.Bucket[][] buckets,
            java.util.function.Predicate<EarthworkCutFillHeatmap.Bucket> pred) {
        for (int x = 0; x < buckets.length; x++) {
            for (int y = 0; y < buckets[x].length; y++) {
                if (pred.test(buckets[x][y])) {
                    return x;
                }
            }
        }
        return -1;
    }

    private static DesignTerrainGrid gridWithCells(DesignTerrainCell... cells) {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        for (DesignTerrainCell cell : cells) {
            grid.put(cell.worldX(), cell.worldZ(), cell);
        }
        return grid;
    }

    private static DesignTerrainCell cell(int x, int z, int deltaY) {
        return cellAtWorld(x, z, new Vec2d(x + 0.5, z + 0.5), deltaY);
    }

    private static DesignTerrainCell cellAtWorld(int worldX, int worldZ, Vec2d canvasCenter, int deltaY) {
        DesignTerrainCell cell = new DesignTerrainCell(worldX, worldZ, canvasCenter, 64);
        cell.setTargetY(64 + deltaY);
        cell.setZoneId("pad");
        return cell;
    }

    private static void assertBucketMatricesEqual(
            EarthworkCutFillHeatmap.Bucket[][] left,
            EarthworkCutFillHeatmap.Bucket[][] right) {
        assertEquals(left.length, right.length);
        for (int x = 0; x < left.length; x++) {
            assertEquals(left[x].length, right[x].length);
            for (int z = 0; z < left[x].length; z++) {
                EarthworkCutFillHeatmap.Bucket l = left[x][z];
                EarthworkCutFillHeatmap.Bucket r = right[x][z];
                assertEquals(l.cutCount(), r.cutCount(), x + "," + z);
                assertEquals(l.fillCount(), r.fillCount(), x + "," + z);
                assertEquals(l.unchangedCount(), r.unchangedCount(), x + "," + z);
            }
        }
    }
}
