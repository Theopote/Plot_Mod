package com.plot.plugin.earthwork.solver;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkSectionCutSpecTest {

    @Test
    void diagonalCutKeepsCellsNearLineSortedAlongDirection() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        // 对角主链 (0,0)..(4,4)，旁侧噪声远离线
        for (int i = 0; i <= 4; i++) {
            put(grid, i, i, 70, 64);
        }
        put(grid, 0, 4, 70, 64);
        put(grid, 4, 0, 70, 64);

        EarthworkSectionCutSpec spec = new EarthworkSectionCutSpec(
            2.5, 2.5, Math.PI / 4.0, 1, true);
        EarthworkSectionProfile profile = EarthworkSectionProfile.fromGrid(grid, spec);
        assertFalse(profile.isEmpty());
        assertTrue(profile.stations().size() >= 4);
        for (int i = 1; i < profile.stations().size(); i++) {
            assertTrue(
                profile.axisDistance(profile.stations().get(i))
                    >= profile.axisDistance(profile.stations().get(i - 1)));
        }
        EarthworkSectionProfile.SectionCutLine line = profile.sectionCutLine();
        assertTrue(Math.abs(line.viewNx() - spec.viewNx()) < 1e-6);
        assertTrue(Math.abs(line.viewNz() - spec.viewNz()) < 1e-6);
    }

    @Test
    void flipViewChangesSignButKeepsStations() {
        DesignTerrainGrid grid = lineAlongX();
        EarthworkSectionCutSpec base = EarthworkSectionCutSpec.horizontal(2.5, 3.5);
        EarthworkSectionProfile a = EarthworkSectionProfile.fromGrid(grid, base);
        EarthworkSectionProfile b = EarthworkSectionProfile.fromGrid(grid, base.flipView());
        assertEquals(a.stations().size(), b.stations().size());
        for (int i = 0; i < a.stations().size(); i++) {
            assertEquals(a.stations().get(i).worldX(), b.stations().get(i).worldX());
            assertEquals(a.stations().get(i).existingY(), b.stations().get(i).existingY());
            assertEquals(a.stations().get(i).designY(), b.stations().get(i).designY());
        }
        assertEquals(a.viewSign(), -b.viewSign());
        assertEquals(-a.viewNx(), b.viewNx(), 1e-9);
        assertEquals(-a.viewNz(), b.viewNz(), 1e-9);
    }

    @Test
    void horizontalManualMatchesAutomaticStationsOnSameRow() {
        DesignTerrainGrid grid = lineAlongX();
        EarthworkSectionProfile auto = EarthworkSectionProfile.fromGrid(grid);
        EarthworkSectionCutSpec horizontal = EarthworkSectionCutSpec.horizontal(2.5, 3.5);
        EarthworkSectionProfile manual = EarthworkSectionProfile.fromGrid(grid, horizontal);
        assertEquals(auto.stations().size(), manual.stations().size());
        for (int i = 0; i < auto.stations().size(); i++) {
            assertEquals(auto.stations().get(i).worldX(), manual.stations().get(i).worldX());
            assertEquals(3, manual.stations().get(i).worldZ());
        }
        assertEquals(0, auto.sectionCutLine().primaryMin());
        assertEquals(4, auto.sectionCutLine().primaryMax());
        assertEquals(3, auto.sectionCutLine().crossMid());
    }

    @Test
    void nudgeMovesOriginAlongViewNormal() {
        EarthworkSectionCutSpec horizontal = EarthworkSectionCutSpec.horizontal(5.0, 5.0);
        EarthworkSectionCutSpec nudged = horizontal.nudgeBlocks(2.0);
        assertEquals(5.0, nudged.originX(), 1e-9);
        assertEquals(7.0, nudged.originZ(), 1e-9);
    }

    private static DesignTerrainGrid lineAlongX() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        for (int x = 0; x < 5; x++) {
            put(grid, x, 3, 70, x < 2 ? 64 : 72);
        }
        return grid;
    }

    private static void put(DesignTerrainGrid grid, int x, int z, int existingY, int targetY) {
        DesignTerrainCell cell = new DesignTerrainCell(x, z, new Vec2d(x + 0.5, z + 0.5), existingY);
        cell.setTargetY(targetY);
        cell.setZoneId("pad");
        grid.put(x, z, cell);
    }
}
