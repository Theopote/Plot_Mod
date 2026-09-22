package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.plugin.building.model.BuildingFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingBlockCountCacheTest {

    private static List<Vec2d> square(double size) {
        return List.of(
            new Vec2d(0, 0),
            new Vec2d(size, 0),
            new Vec2d(size, size),
            new Vec2d(0, size));
    }

    @Test
    void countsCanvasAlignedFootprintCellsWithoutProjection() {
        BuildingBlockCountCache cache = new BuildingBlockCountCache();
        BuildingFootprint footprint = new BuildingFootprint("fp", square(5), true);
        assertEquals(25, cache.blockCount(footprint, WorldProjectionSnapshot.UNKNOWN));
    }

    @Test
    void cachesByGeometryAndProjectionFingerprint() {
        BuildingBlockCountCache cache = new BuildingBlockCountCache();
        BuildingFootprint footprint = new BuildingFootprint("fp", square(4), true);
        WorldProjectionSnapshot projection = WorldProjectionSnapshot.UNKNOWN;

        int first = cache.blockCount(footprint, projection);
        int second = cache.blockCount(footprint, projection);

        assertEquals(16, first);
        assertEquals(first, second);
    }

    @Test
    void retainOnlyIsScopedToSessionInstance() {
        BuildingBlockCountCache cache = new BuildingBlockCountCache();
        BuildingFootprint kept = new BuildingFootprint("kept", square(4), true);
        BuildingFootprint removed = new BuildingFootprint("removed", square(5), true);
        WorldProjectionSnapshot projection = WorldProjectionSnapshot.UNKNOWN;

        assertEquals(16, cache.blockCount(kept, projection));
        assertEquals(25, cache.blockCount(removed, projection));

        cache.retainOnly(Set.of("kept"));

        assertEquals(25, cache.blockCount(removed, projection));
    }
}
