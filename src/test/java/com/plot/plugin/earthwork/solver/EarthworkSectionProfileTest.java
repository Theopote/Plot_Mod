package com.plot.plugin.earthwork.solver;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkSectionProfileTest {

    @Test
    void tracesLongerAxisAndLabelsCutFill() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        for (int x = 0; x < 5; x++) {
            DesignTerrainCell cell = new DesignTerrainCell(x, 3, new Vec2d(x + 0.5, 3.5), 70);
            cell.setTargetY(x < 2 ? 64 : 72);
            cell.setZoneId("pad");
            grid.put(x, 3, cell);
        }
        EarthworkSectionProfile profile = EarthworkSectionProfile.fromGrid(grid);
        assertTrue(profile.alongX());
        assertEquals(5, profile.stations().size());
        assertEquals(0, profile.stations().get(0).worldX());
        assertEquals(4, profile.stations().get(4).worldX());
        assertTrue(profile.stations().get(0).cut() > 0);
        assertTrue(profile.stations().get(4).fill() > 0);
        assertNotEquals(profile.stations().get(0).existingY(), profile.stations().get(0).designY());
    }
}
