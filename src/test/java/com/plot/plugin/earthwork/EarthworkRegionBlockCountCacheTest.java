package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.plugin.earthwork.model.GradingRegion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EarthworkRegionBlockCountCacheTest {

    private static List<Vec2d> square(double size) {
        return List.of(
            new Vec2d(0, 0),
            new Vec2d(size, 0),
            new Vec2d(size, size),
            new Vec2d(0, size));
    }

    @Test
    void countsCanvasAlignedRegionCellsWithoutProjection() {
        EarthworkRegionBlockCountCache cache = new EarthworkRegionBlockCountCache();
        GradingRegion region = new GradingRegion("r1", square(5));
        assertEquals(25, cache.blockCount(region, WorldProjectionSnapshot.UNKNOWN));
    }

    @Test
    void cachesByGeometryAndProjectionFingerprint() {
        EarthworkRegionBlockCountCache cache = new EarthworkRegionBlockCountCache();
        GradingRegion region = new GradingRegion("r1", square(4));
        WorldProjectionSnapshot projection = WorldProjectionSnapshot.UNKNOWN;

        assertEquals(16, cache.blockCount(region, projection));
        assertEquals(16, cache.blockCount(region, projection));
    }

    @Test
    void retainOnlyDropsRemovedRegions() {
        EarthworkRegionBlockCountCache cache = new EarthworkRegionBlockCountCache();
        GradingRegion kept = new GradingRegion("kept", square(4));
        GradingRegion removed = new GradingRegion("removed", square(5));
        WorldProjectionSnapshot projection = WorldProjectionSnapshot.UNKNOWN;

        assertEquals(16, cache.blockCount(kept, projection));
        assertEquals(25, cache.blockCount(removed, projection));

        cache.retainOnly(Set.of("kept"));

        assertEquals(25, cache.blockCount(removed, projection));
    }
}
