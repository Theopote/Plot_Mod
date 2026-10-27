package com.plot.plugin.earthwork.solver;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 凹形 / 带孔 footprint：剖面仍落在单条截线上。 */
class EarthworkSectionProfileHoleTest {

    @Test
    void concaveOutlineUsesSingleCrossCoordinate() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        // U 形：z=3 整行 x=0..4，z=2 仅两侧立柱，中间 (2,2) 空缺
        for (int x = 0; x <= 4; x++) {
            put(grid, x, 3, 64);
        }
        put(grid, 0, 2, 64);
        put(grid, 4, 2, 64);

        EarthworkSectionProfile profile = EarthworkSectionProfile.fromGrid(grid);
        assertTrue(profile.alongX());
        assertTrue(profile.stations().size() >= 2);
        int cross = profile.crossAxisCoordinate();
        for (EarthworkSectionProfile.Station station : profile.stations()) {
            assertEquals(cross, station.worldZ());
        }
        EarthworkSectionProfile.SectionCutLine line = profile.sectionCutLine();
        assertEquals(cross, line.crossMid());
    }

    private static void put(DesignTerrainGrid grid, int x, int z, int existingY) {
        DesignTerrainCell cell = new DesignTerrainCell(x, z, new Vec2d(x + 0.5, z + 0.5), existingY);
        cell.setTargetY(existingY);
        cell.setZoneId("pad");
        grid.put(x, z, cell);
    }
}
