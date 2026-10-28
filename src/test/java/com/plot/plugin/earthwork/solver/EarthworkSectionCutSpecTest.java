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
                profile.axisDistanceExact(profile.stations().get(i))
                    >= profile.axisDistanceExact(profile.stations().get(i - 1)) - 1e-9);
            if (i > 0) {
                assertFalse(
                    profile.shouldBreakPolyline(
                        profile.stations().get(i - 1), profile.stations().get(i)),
                    "diagonal neighbors must stay connected on the chart");
            }
        }
        EarthworkSectionProfile.SectionCutLine line = profile.sectionCutLine();
        assertTrue(Math.abs(line.viewNx() - spec.viewNx()) < 1e-6);
        assertTrue(Math.abs(line.viewNz() - spec.viewNz()) < 1e-6);
    }

    @Test
    void diagonalCutIncludesBothSeparatedZones() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        // 区 A：左下挖；区 B：右上填；中间空隙；45° 截线应两区都进站
        for (int i = 0; i <= 2; i++) {
            put(grid, i, i, 70, 64, "zone-a"); // cut
        }
        for (int i = 6; i <= 8; i++) {
            put(grid, i, i, 60, 72, "zone-b"); // fill
        }
        // 场地空白（无 zone）不进剖面站
        for (int i = 3; i <= 5; i++) {
            DesignTerrainCell padding = new DesignTerrainCell(i, i, new Vec2d(i + 0.5, i + 0.5), 65);
            padding.setTargetY(65);
            grid.put(i, i, padding);
        }

        EarthworkSectionCutSpec spec = new EarthworkSectionCutSpec(
            4.5, 4.5, Math.PI / 4.0, 1, true);
        EarthworkSectionProfile profile = EarthworkSectionProfile.fromGrid(grid, spec);
        assertFalse(profile.isEmpty());
        long cutStations = profile.stations().stream().filter(s -> s.cut() > 0).count();
        long fillStations = profile.stations().stream().filter(s -> s.fill() > 0).count();
        assertTrue(cutStations >= 2, "lower-left zone cut must appear");
        assertTrue(fillStations >= 2, "upper-right zone fill must appear");
        double lastCutAlong = profile.stations().stream()
            .filter(s -> s.cut() > 0)
            .mapToDouble(profile::axisDistanceExact)
            .max()
            .orElse(0);
        double firstFillAlong = profile.stations().stream()
            .filter(s -> s.fill() > 0)
            .mapToDouble(profile::axisDistanceExact)
            .min()
            .orElse(0);
        assertTrue(lastCutAlong < firstFillAlong, "cut zone must precede fill zone along cut");
        boolean sawBreak = false;
        for (int i = 1; i < profile.stations().size(); i++) {
            if (profile.shouldBreakPolyline(profile.stations().get(i - 1), profile.stations().get(i))) {
                sawBreak = true;
                break;
            }
        }
        assertTrue(sawBreak, "unzoned gap between zones must break polyline");
        assertEquals(2, profile.stations().stream().map(EarthworkSectionProfile.Station::zoneId).distinct().count());
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

    @Test
    void missAllZonesKeepsMinimumDrawableCutLine() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        put(grid, 0, 0, 70, 64, "zone-a");
        put(grid, 1, 0, 70, 64, "zone-a");
        // 截线原点远离分区，无交点
        EarthworkSectionCutSpec miss = new EarthworkSectionCutSpec(100.0, 100.0, 0.0, 1, true);
        EarthworkSectionProfile profile = EarthworkSectionProfile.fromGrid(grid, miss);
        assertTrue(profile.isEmpty(), "chart has no stations");
        assertTrue(profile.hasDrawableCutLine(), "canvas guide line remains");
        EarthworkSectionProfile.SectionCutLine line = profile.sectionCutLine();
        double length = Math.hypot(line.endWorldX() - line.startWorldX(), line.endWorldZ() - line.startWorldZ());
        assertTrue(
            length >= EarthworkSectionProfile.MIN_CUT_LINE_HALF_LENGTH * 2.0 - 1e-6,
            "cut line must keep minimum length");
    }

    private static DesignTerrainGrid lineAlongX() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        for (int x = 0; x < 5; x++) {
            put(grid, x, 3, 70, x < 2 ? 64 : 72);
        }
        return grid;
    }

    private static void put(DesignTerrainGrid grid, int x, int z, int existingY, int targetY) {
        put(grid, x, z, existingY, targetY, "pad");
    }

    private static void put(DesignTerrainGrid grid, int x, int z, int existingY, int targetY, String zoneId) {
        DesignTerrainCell cell = new DesignTerrainCell(x, z, new Vec2d(x + 0.5, z + 0.5), existingY);
        cell.setTargetY(targetY);
        cell.setZoneId(zoneId);
        grid.put(x, z, cell);
    }
}
