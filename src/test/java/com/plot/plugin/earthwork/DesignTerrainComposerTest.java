package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.solver.ZoneAllocationBalanceAdjuster;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;
import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.RegionGeometry;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.ExclusionZone;
import com.plot.plugin.earthwork.model.GradingZone;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignTerrainComposerTest {

    @Test
    void higherPriorityZoneWinsInOverlap() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));

        GradingZone yard = new GradingZone(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));
        yard.setPriority(50);
        yard.getRegion().setAutoBalance(false);
        yard.getRegion().setManualTargetElevation(60);

        GradingZone pad = new GradingZone(List.of(
            new Vec2d(2, 2),
            new Vec2d(8, 2),
            new Vec2d(8, 8),
            new Vec2d(2, 8)
        ));
        pad.setPriority(100);
        pad.getRegion().setAutoBalance(false);
        pad.getRegion().setManualTargetElevation(75);

        site.addZone(yard);
        site.addZone(pad);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_PER_ZONE);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 65),
            new TerrainSnapshot.Column(new Vec2d(1, 1), 1, 1, 65)
        ));

        DesignTerrainComposer.ComposeResult result = DesignTerrainComposer.compose(site, terrain, null);
        DesignTerrainCell inner = result.grid().get(5, 5);
        DesignTerrainCell outerOnly = result.grid().get(1, 1);

        assertEquals(75, inner.targetY());
        assertEquals(pad.getId(), inner.zoneId());
        assertEquals(60, outerOnly.targetY());
        assertEquals(yard.getId(), outerOnly.zoneId());
    }

    @Test
    void exclusionZonePreservesExistingGround() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));

        GradingZone zone = new GradingZone(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));
        zone.getRegion().setAutoBalance(false);
        zone.getRegion().setManualTargetElevation(50);
        site.addZone(zone);

        ExclusionZone exclusion = new ExclusionZone("ex-1");
        exclusion.setOuterPoints(List.of(
            new Vec2d(3, 3),
            new Vec2d(7, 3),
            new Vec2d(7, 7),
            new Vec2d(3, 7)
        ));
        exclusion.setMode(ExclusionZone.MODE_PRESERVE_EXISTING);
        site.setExclusionZones(List.of(exclusion));

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 68),
            new TerrainSnapshot.Column(new Vec2d(1, 1), 1, 1, 62)
        ));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();
        DesignTerrainCell excluded = grid.get(5, 5);
        DesignTerrainCell graded = grid.get(1, 1);

        assertTrue(excluded.excluded());
        assertEquals(68, excluded.targetY());
        assertEquals(50, graded.targetY());
    }

    @Test
    void smallerAreaWinsWhenPriorityTied() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));

        GradingZone large = new GradingZone("zone-large", List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));
        large.setPriority(50);
        large.getRegion().setAutoBalance(false);
        large.getRegion().setManualTargetElevation(60);

        GradingZone small = new GradingZone("zone-small", List.of(
            new Vec2d(2, 2),
            new Vec2d(8, 2),
            new Vec2d(8, 8),
            new Vec2d(2, 8)
        ));
        small.setPriority(50);
        small.getRegion().setAutoBalance(false);
        small.getRegion().setManualTargetElevation(72);

        site.addZone(large);
        site.addZone(small);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_PER_ZONE);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 65)
        ));

        DesignTerrainCell cell = DesignTerrainComposer.compose(site, terrain, null).grid().get(5, 5);
        assertEquals(72, cell.targetY());
        assertEquals("zone-small", cell.zoneId());
    }

    @Test
    void siteWideZoneAllocationAdjustsTargetsPerZoneEndToEnd() {
        EarthworkSite site = adjacentCutFillZones(60, 72);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_SITE_WIDE);
        site.getCompositionPolicy().setBalanceMethod(CompositionPolicy.BALANCE_METHOD_ZONE_ALLOCATION);
        site.getCompositionPolicy().setBalanceResidualUniformPolish(true);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(2, 5), 2, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(12, 5), 12, 5, 60),
            new TerrainSnapshot.Column(new Vec2d(15, 5), 15, 5, 60)
        ));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();

        Map<String, Integer> offsets = site.getLastZoneVerticalOffsets();
        assertEquals(10, offsets.get("zone-cut"));
        assertEquals(-12, offsets.get("zone-fill"));
        assertEquals(0, site.getLastSiteWideVerticalOffset());

        assertEquals(70, grid.get(2, 5).targetY());
        assertEquals(70, grid.get(5, 5).targetY());
        assertEquals(60, grid.get(12, 5).targetY());
        assertEquals(60, grid.get(15, 5).targetY());

        SiteEarthworkReport volumes = ZoneAllocationBalanceAdjuster.collectZoneVolumes(grid);
        assertEquals(0L, volumes.totals().geometricCutVolume());
        assertEquals(0L, volumes.totals().geometricFillVolume());
    }

    @Test
    void siteWideUniformOffsetBalancesFieldEndToEnd() {
        EarthworkSite site = adjacentCutFillZones(60, 68);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_SITE_WIDE);
        site.getCompositionPolicy().setBalanceMethod(CompositionPolicy.BALANCE_METHOD_UNIFORM);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(2, 5), 2, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 72),
            new TerrainSnapshot.Column(new Vec2d(12, 5), 12, 5, 58)
        ));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();

        assertTrue(site.getLastZoneVerticalOffsets().isEmpty());
        int uniformOffset = site.getLastSiteWideVerticalOffset();
        assertNotEquals(0, uniformOffset);

        assertEquals(60 + uniformOffset, grid.get(2, 5).targetY());
        assertEquals(60 + uniformOffset, grid.get(5, 5).targetY());
        assertEquals(68 + uniformOffset, grid.get(12, 5).targetY());

        SiteEarthworkReport volumes = ZoneAllocationBalanceAdjuster.collectZoneVolumes(grid);
        long cut = volumes.totals().geometricCutVolume();
        long fill = volumes.totals().geometricFillVolume();
        assertTrue(Math.abs(cut - fill) <= 3L,
            () -> "expected near-balanced volumes, cut=" + cut + " fill=" + fill);
    }

    @Test
    void perZoneScopePreservesDesignSurfaceWithoutSiteBalance() {
        EarthworkSite site = adjacentCutFillZones(60, 72);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_PER_ZONE);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(2, 5), 2, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(12, 5), 12, 5, 60)
        ));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();

        assertTrue(site.getLastZoneVerticalOffsets().isEmpty());
        assertEquals(0, site.getLastSiteWideVerticalOffset());
        assertEquals(60, grid.get(2, 5).targetY());
        assertEquals(72, grid.get(12, 5).targetY());
    }

    @Test
    void gradingZoneWithHoleSkipsInteriorCellsEndToEnd() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0),
            new Vec2d(12, 0),
            new Vec2d(12, 12),
            new Vec2d(0, 12)
        ));

        GradingZone zone = new GradingZone("zone-donut", RegionGeometry.of(
            List.of(
                new Vec2d(0, 0),
                new Vec2d(12, 0),
                new Vec2d(12, 12),
                new Vec2d(0, 12)),
            List.of(List.of(
                new Vec2d(4, 4),
                new Vec2d(8, 4),
                new Vec2d(8, 8),
                new Vec2d(4, 8)))));
        zone.getRegion().setAutoBalance(false);
        zone.getRegion().setManualTargetElevation(55);
        site.addZone(zone);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_PER_ZONE);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(1, 1), 1, 1, 65),
            new TerrainSnapshot.Column(new Vec2d(6, 6), 6, 6, 65),
            new TerrainSnapshot.Column(new Vec2d(10, 10), 10, 10, 65)
        ));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();

        assertEquals(55, grid.get(1, 1).targetY());
        assertEquals(65, grid.get(6, 6).targetY());
        assertEquals(55, grid.get(10, 10).targetY());
        assertEquals("zone-donut", grid.get(1, 1).zoneId());
        assertNull(grid.get(6, 6).zoneId());
    }

    @Test
    void exclusionZoneWithHolePreservesOnlyRingEndToEnd() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0),
            new Vec2d(12, 0),
            new Vec2d(12, 12),
            new Vec2d(0, 12)
        ));

        GradingZone zone = new GradingZone(List.of(
            new Vec2d(0, 0),
            new Vec2d(12, 0),
            new Vec2d(12, 12),
            new Vec2d(0, 12)
        ));
        zone.getRegion().setAutoBalance(false);
        zone.getRegion().setManualTargetElevation(50);
        site.addZone(zone);

        ExclusionZone exclusion = new ExclusionZone("courtyard");
        exclusion.setGeometry(RegionGeometry.of(
            List.of(
                new Vec2d(2, 2),
                new Vec2d(10, 2),
                new Vec2d(10, 10),
                new Vec2d(2, 10)),
            List.of(List.of(
                new Vec2d(4, 4),
                new Vec2d(8, 4),
                new Vec2d(8, 8),
                new Vec2d(4, 8)))));
        exclusion.setMode(ExclusionZone.MODE_PRESERVE_EXISTING);
        site.setExclusionZones(List.of(exclusion));

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(3, 3), 3, 3, 68),
            new TerrainSnapshot.Column(new Vec2d(6, 6), 6, 6, 68),
            new TerrainSnapshot.Column(new Vec2d(9, 9), 9, 9, 68)
        ));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();

        assertTrue(grid.get(3, 3).excluded());
        assertEquals(68, grid.get(3, 3).targetY());
        assertFalse(grid.get(6, 6).excluded());
        assertEquals(50, grid.get(6, 6).targetY());
        assertTrue(grid.get(9, 9).excluded());
    }

    private static EarthworkSite adjacentCutFillZones(int cutElevation, int fillElevation) {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0),
            new Vec2d(20, 0),
            new Vec2d(20, 10),
            new Vec2d(0, 10)
        ));

        GradingZone cut = new GradingZone("zone-cut", List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));
        cut.setPriority(50);
        cut.getRegion().setAutoBalance(false);
        cut.getRegion().setManualTargetElevation(cutElevation);

        GradingZone fill = new GradingZone("zone-fill", List.of(
            new Vec2d(10, 0),
            new Vec2d(20, 0),
            new Vec2d(20, 10),
            new Vec2d(10, 10)
        ));
        fill.setPriority(50);
        fill.getRegion().setAutoBalance(false);
        fill.getRegion().setManualTargetElevation(fillElevation);

        site.addZone(cut);
        site.addZone(fill);
        return site;
    }
}
