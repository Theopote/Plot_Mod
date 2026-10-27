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
        assertEquals(0, profile.axisDistance(profile.stations().get(0)));
        assertEquals(4, profile.axisDistance(profile.stations().get(4)));
        assertEquals(4, profile.axisSpan());
        assertEquals(1, profile.axisGap(profile.stations().get(0), profile.stations().get(1)));
        EarthworkSectionProfile.SectionCutLine line = profile.sectionCutLine();
        assertEquals(0, line.primaryMin());
        assertEquals(4, line.primaryMax());
        assertEquals(3, line.crossMid());
        assertEquals(3, profile.crossAxisCoordinate());
        for (EarthworkSectionProfile.Station station : profile.stations()) {
            assertEquals(3, station.worldZ());
        }
    }

    @Test
    void picksParallelCutLineInsteadOfMixingColumns() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        for (int x = 0; x < 5; x++) {
            DesignTerrainCell cell = new DesignTerrainCell(x, 4, new Vec2d(x + 0.5, 4.5), 64);
            cell.setTargetY(64);
            cell.setZoneId("pad");
            grid.put(x, 4, cell);
        }
        DesignTerrainCell lone = new DesignTerrainCell(0, 3, new Vec2d(0.5, 3.5), 64);
        lone.setTargetY(64);
        lone.setZoneId("pad");
        grid.put(0, 3, lone);

        EarthworkSectionProfile profile = EarthworkSectionProfile.fromGrid(grid);
        assertEquals(5, profile.stations().size());
        assertEquals(4, profile.crossAxisCoordinate());
        for (EarthworkSectionProfile.Station station : profile.stations()) {
            assertEquals(4, station.worldZ());
        }
    }

    @Test
    void axisGapBreaksWhenStationsAreNotAdjacent() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        for (int x : new int[] {0, 2, 5}) {
            DesignTerrainCell cell = new DesignTerrainCell(x, 3, new Vec2d(x + 0.5, 3.5), 64);
            cell.setTargetY(64);
            cell.setZoneId("pad");
            grid.put(x, 3, cell);
        }
        EarthworkSectionProfile profile = EarthworkSectionProfile.fromGrid(grid);
        assertEquals(3, profile.stations().size());
        assertEquals(2, profile.axisGap(profile.stations().get(0), profile.stations().get(1)));
    }
}
