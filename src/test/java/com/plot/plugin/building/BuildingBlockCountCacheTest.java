package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.plugin.building.model.BuildingFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        BuildingFootprint footprint = new BuildingFootprint("fp", square(5), true);
        assertEquals(25, BuildingBlockCountCache.blockCount(footprint, WorldProjectionSnapshot.UNKNOWN));
    }

    @Test
    void cachesByGeometryAndProjectionFingerprint() {
        BuildingFootprint footprint = new BuildingFootprint("fp", square(4), true);
        WorldProjectionSnapshot projection = WorldProjectionSnapshot.UNKNOWN;

        int first = BuildingBlockCountCache.blockCount(footprint, projection);
        int second = BuildingBlockCountCache.blockCount(footprint, projection);

        assertEquals(16, first);
        assertEquals(first, second);
    }
}
