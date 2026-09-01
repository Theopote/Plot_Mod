package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.Polygon;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainSnapshotTest {

    @Test
    void previewColumnsSubsetOfFullFootprint() {
        TerrainSnapshot snapshot = TerrainSnapshot.forColumns(List.of(
            column(0, 0, 64),
            column(1, 0, 65),
            column(2, 0, 66),
            column(3, 0, 67),
            column(4, 0, 68),
            column(5, 0, 69)
        ));

        assertEquals(6, snapshot.columnCount());
        assertTrue(snapshot.previewColumns(5).size() < snapshot.columnCount());
        assertEquals(snapshot.columnCount(), snapshot.previewColumns(1).size());
    }

    @Test
    void heightSamplesAlignWithColumns() {
        TerrainSnapshot snapshot = TerrainSnapshot.forColumns(List.of(
            column(2, 3, 70),
            column(4, 5, 72)
        ));

        assertEquals(List.of(70, 72), snapshot.groundHeights());
        assertEquals(2, snapshot.heightSamples().size());
        assertEquals(70, snapshot.heightSamples().getFirst().groundY());
    }

    @Test
    void captureWithoutWorldUsesDefaultElevation() {
        List<Vec2d> triangle = List.of(new Vec2d(0, 0), new Vec2d(6, 0), new Vec2d(0, 6));
        Polygon polygon = EarthworkGeometryUtils.toPolygon(triangle);
        TerrainSnapshot snapshot = TerrainSnapshot.capture(null, polygon, triangle, null);

        assertTrue(snapshot.columnCount() > 0);
        assertTrue(snapshot.groundHeights().stream().allMatch(height -> height == 64));
    }

    @Test
    void matchesPreviewGridDelegatesToGeometryUtils() {
        Vec2d center = new Vec2d(10.5, 15.5);
        assertTrue(EarthworkGeometryUtils.matchesPreviewGrid(center, 1));
        assertTrue(EarthworkGeometryUtils.matchesPreviewGrid(new Vec2d(10.5, 20.5), 5));
        assertTrue(!EarthworkGeometryUtils.matchesPreviewGrid(new Vec2d(11.5, 20.5), 5));
    }

    private static TerrainSnapshot.Column column(int x, int z, int groundY) {
        return new TerrainSnapshot.Column(new Vec2d(x + 0.5, z + 0.5), x, z, groundY);
    }
}
