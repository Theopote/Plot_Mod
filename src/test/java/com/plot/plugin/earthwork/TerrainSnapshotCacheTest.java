package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.GradingRegion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainSnapshotCacheTest {

    @Test
    void reusesCachedSnapshotForSameRegionOutlineAndWorld() {
        TerrainSnapshotCache cache = new TerrainSnapshotCache();
        GradingRegion region = rectangleRegion();

        TerrainSnapshot first = cache.getOrCapture(region, null, null);
        TerrainSnapshot second = cache.getOrCapture(region, null, null);

        assertTrue(first.columnCount() > 0);
        assertSame(first, second);
        assertTrue(cache.isCached(region.getId()));
    }

    @Test
    void recapturesWhenOutlineChanges() {
        TerrainSnapshotCache cache = new TerrainSnapshotCache();
        GradingRegion region = rectangleRegion();

        TerrainSnapshot first = cache.getOrCapture(region, null, null);
        region.setOuterPoints(List.of(
            new Vec2d(0, 0),
            new Vec2d(20, 0),
            new Vec2d(20, 12),
            new Vec2d(0, 12)
        ));
        TerrainSnapshot second = cache.getOrCapture(region, null, null);

        assertNotSame(first, second);
        assertTrue(second.columnCount() > first.columnCount());
    }

    @Test
    void invalidateRegionForcesRecapture() {
        TerrainSnapshotCache cache = new TerrainSnapshotCache();
        GradingRegion region = rectangleRegion();

        TerrainSnapshot first = cache.getOrCapture(region, null, null);
        cache.invalidateRegion(region.getId());
        assertTrue(!cache.isCached(region.getId()));

        TerrainSnapshot second = cache.getOrCapture(region, null, null);
        assertNotSame(first, second);
    }

    @Test
    void outlineFingerprintIsStable() {
        List<Vec2d> points = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 8)
        );
        assertTrue(TerrainSnapshotCache.outlineFingerprint(points)
            == TerrainSnapshotCache.outlineFingerprint(points));
    }

    private static GradingRegion rectangleRegion() {
        return new GradingRegion(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 8),
            new Vec2d(0, 8)
        ));
    }
}
