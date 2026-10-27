package com.plot.plugin.earthwork.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EarthworkCutFillHeatmapTest {

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

    private static DesignTerrainGrid gridWithCells(DesignTerrainCell... cells) {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        for (DesignTerrainCell cell : cells) {
            grid.put(cell.worldX(), cell.worldZ(), cell);
        }
        return grid;
    }

    private static DesignTerrainCell cell(int x, int z, int deltaY) {
        DesignTerrainCell cell = new DesignTerrainCell(x, z, new Vec2d(x + 0.5, z + 0.5), 64);
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
