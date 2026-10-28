package com.plot.plugin.earthwork.solver;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkSectionProfileZoneTest {

    @Test
    void diagonalPerZoneExcludesOtherZoneCells() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        for (int i = 0; i <= 2; i++) {
            put(grid, i, i, 70, 64, "zone-a");
        }
        for (int i = 6; i <= 8; i++) {
            put(grid, i, i, 60, 72, "zone-b");
        }
        // 区间空白
        for (int i = 3; i <= 5; i++) {
            putExterior(grid, i, i, 65);
        }

        EarthworkSectionCutSpec diagonal = new EarthworkSectionCutSpec(4.5, 4.5, Math.PI / 4.0, 1, true);
        EarthworkSectionProfile profileA =
            EarthworkSectionProfile.fromGridForZone(grid, "zone-a", diagonal);
        EarthworkSectionProfile profileB =
            EarthworkSectionProfile.fromGridForZone(grid, "zone-b", diagonal);

        assertFalse(profileA.isEmpty());
        assertFalse(profileB.isEmpty());
        assertTrue(profileA.hasDrawableCutLine());
        assertTrue(profileB.hasDrawableCutLine());

        assertTrue(
            profileA.stations().stream().noneMatch(s -> "zone-b".equals(s.zoneId())),
            "zone-a profile must not contain zone-b cells");
        assertTrue(
            profileB.stations().stream().noneMatch(s -> "zone-a".equals(s.zoneId())),
            "zone-b profile must not contain zone-a cells");
        assertTrue(profileA.stations().stream().anyMatch(s -> "zone-a".equals(s.zoneId())));
        assertTrue(profileB.stations().stream().anyMatch(s -> "zone-b".equals(s.zoneId())));
    }

    @Test
    void missKeepsGuideForThatZoneOnly() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        for (int x = 0; x < 4; x++) {
            put(grid, x, 0, 70, 64, "zone-a");
            put(grid, x + 10, 5, 60, 72, "zone-b");
        }
        EarthworkSectionCutSpec missA = new EarthworkSectionCutSpec(1.5, 80.0, 0.0, 1, true);

        EarthworkSectionProfile aMiss =
            EarthworkSectionProfile.fromGridForZone(grid, "zone-a", missA);
        // B 用穿过自身的水平线，不受 A 旋偏影响
        EarthworkSectionProfile bOk =
            EarthworkSectionProfile.fromGridForZone(
                grid, "zone-b", EarthworkSectionCutSpec.horizontal(12.0, 5.5));

        assertTrue(aMiss.isEmpty());
        assertTrue(aMiss.hasDrawableCutLine(), "missed zone still has guide line");
        assertFalse(bOk.isEmpty());
        assertTrue(bOk.hasDrawableCutLine());
        assertEquals(
            5,
            bOk.stations().stream()
                .filter(s -> "zone-b".equals(s.zoneId()))
                .findFirst()
                .orElseThrow()
                .worldZ());
    }

    @Test
    void marginAddsExteriorStationsWithExistingEqualsDesign() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        // 小区：x=10..12 on z=5
        for (int x = 10; x <= 12; x++) {
            put(grid, x, 5, 70, 64, "pad");
        }
        // 沿程外扩空白格
        for (int x = 10 - EarthworkSectionProfile.ZONE_SECTION_MARGIN_BLOCKS;
             x <= 12 + EarthworkSectionProfile.ZONE_SECTION_MARGIN_BLOCKS;
             x++) {
            if (x >= 10 && x <= 12) {
                continue;
            }
            putExterior(grid, x, 5, 68);
        }

        EarthworkSectionProfile profile = EarthworkSectionProfile.fromGridForZone(
            grid, "pad", EarthworkSectionCutSpec.AUTOMATIC);
        assertFalse(profile.isEmpty());
        long exterior = profile.stations().stream().filter(s -> !s.zoned()).count();
        assertTrue(exterior >= 2, "margin must include exterior stations");
        for (EarthworkSectionProfile.Station station : profile.stations()) {
            if (!station.zoned()) {
                assertEquals(
                    station.existingY(),
                    station.designY(),
                    "exterior station must show existing only (green=gray)");
            }
        }
        assertTrue(profile.stations().stream().anyMatch(s -> "pad".equals(s.zoneId())));
    }

    @Test
    void bothZonesHaveDrawableCutLinesIndependently() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        for (int x = 0; x < 5; x++) {
            put(grid, x, 0, 70, 64, "zone-a");
            put(grid, x, 20, 60, 72, "zone-b");
        }
        EarthworkSectionProfile a =
            EarthworkSectionProfile.fromGridForZone(grid, "zone-a", EarthworkSectionCutSpec.AUTOMATIC);
        EarthworkSectionProfile b =
            EarthworkSectionProfile.fromGridForZone(grid, "zone-b", EarthworkSectionCutSpec.AUTOMATIC);
        assertTrue(a.hasDrawableCutLine());
        assertTrue(b.hasDrawableCutLine());
        assertFalse(a.isEmpty());
        assertFalse(b.isEmpty());
    }

    private static void put(DesignTerrainGrid grid, int x, int z, int existingY, int targetY, String zoneId) {
        DesignTerrainCell cell = new DesignTerrainCell(x, z, new Vec2d(x + 0.5, z + 0.5), existingY);
        cell.setTargetY(targetY);
        cell.setZoneId(zoneId);
        grid.put(x, z, cell);
    }

    private static void putExterior(DesignTerrainGrid grid, int x, int z, int existingY) {
        DesignTerrainCell cell = new DesignTerrainCell(x, z, new Vec2d(x + 0.5, z + 0.5), existingY);
        cell.setTargetY(existingY + 5); // 有 target 也不应进设计：fromGridForZone 强制绿=灰
        grid.put(x, z, cell);
    }
}
