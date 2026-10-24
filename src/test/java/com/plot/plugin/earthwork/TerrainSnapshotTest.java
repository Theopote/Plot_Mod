package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.Polygon;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void captureWithoutWorldMarksColumnsUnloaded() {
        List<Vec2d> triangle = List.of(new Vec2d(0, 0), new Vec2d(6, 0), new Vec2d(0, 6));
        Polygon polygon = EarthworkGeometryUtils.toPolygon(triangle);
        TerrainSnapshot snapshot = TerrainSnapshot.capture(null, polygon, triangle, null);

        assertTrue(snapshot.columnCount() > 0);
        assertEquals(snapshot.columnCount(), snapshot.unloadedColumnCount());
        assertTrue(snapshot.groundHeights().isEmpty());
        assertTrue(snapshot.centers().isEmpty());
        assertTrue(snapshot.previewColumns(1).isEmpty());
        assertTrue(snapshot.previewHeightSamples(100).isEmpty());
        assertTrue(snapshot.exceedsUnloadedThreshold());
    }

    @Test
    void unloadedColumnsExcludedFromGroundHeightsAndVolumes() {
        TerrainSnapshot snapshot = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(0.5, 0.5), 0, 0, 70, "minecraft:grass_block", true),
            new TerrainSnapshot.Column(new Vec2d(1.5, 0.5), 1, 0, 64, "", false),
            new TerrainSnapshot.Column(new Vec2d(2.5, 0.5), 2, 0, 72, "minecraft:grass_block", true)
        ));
        assertEquals(List.of(70, 72), snapshot.groundHeights());
        assertEquals(1, snapshot.unloadedColumnCount());
        assertEquals(List.of(new Vec2d(0.5, 0.5), new Vec2d(2.5, 0.5)), snapshot.centers());
        assertEquals(2, snapshot.previewColumns(1).size());
        assertTrue(snapshot.previewHeightSamples(100).stream().noneMatch(sample -> sample.groundY() == 64));
        assertTrue(snapshot.previewColumns(100).stream().allMatch(TerrainSnapshot.Column::chunkLoaded));
        assertEquals(2, snapshot.loadedColumnCount());
        // 1/3 > 5% → 超过门禁
        assertTrue(snapshot.exceedsUnloadedThreshold());
    }

    @Test
    void exceedsUnloadedThresholdMatchesPatternGate() {
        assertFalse(TerrainSnapshot.exceedsUnloadedThreshold(0, 100));
        assertFalse(TerrainSnapshot.exceedsUnloadedThreshold(4, 100));
        assertTrue(TerrainSnapshot.exceedsUnloadedThreshold(10, 100));
        assertTrue(TerrainSnapshot.exceedsUnloadedThreshold(6, 100));
        assertFalse(TerrainSnapshot.exceedsUnloadedThreshold(1, 100));
    }

    @Test
    void contentFingerprintDetectsGroundHeightChange() {
        TerrainSnapshot before = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(0.5, 0.5), 0, 0, 64, "minecraft:grass_block", true)));
        TerrainSnapshot after = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(0.5, 0.5), 0, 0, 70, "minecraft:grass_block", true)));
        assertTrue(before.contentFingerprint() != after.contentFingerprint());
    }

    @Test
    void emptySnapshotComparisonMatches() {
        TerrainSnapshot.ComparisonResult result = TerrainSnapshot.empty().compareWithCurrentWorld(null);
        assertTrue(result.matches());
    }

    @Test
    void comparisonResultSurfaceOnlyDriftDoesNotCountAsTerrainChanged() {
        TerrainSnapshot.ComparisonResult cosmetic = new TerrainSnapshot.ComparisonResult(
            true, 25, 622644, 0, 25);
        assertTrue(cosmetic.matches());
        assertTrue(!cosmetic.terrainChanged());
        assertTrue(cosmetic.hasCosmeticSurfaceDrift());

        TerrainSnapshot.ComparisonResult elevation = new TerrainSnapshot.ComparisonResult(
            false, 3, 1000, 3, 10);
        assertTrue(elevation.terrainChanged());
        assertTrue(!elevation.hasCosmeticSurfaceDrift());
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
