package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.design.BuildingFootprintLookup;
import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.design.RoadSurfaceLookup;
import com.plot.plugin.earthwork.geometry.ZoneBoundarySlopeApplicator;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.solver.EarthworkAllocationMatrix;
import com.plot.plugin.earthwork.solver.EarthworkOptimizationSolver;
import com.plot.plugin.earthwork.solver.SlopeCoupledVerticalSearch;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;
import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.RegionGeometry;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.EdgeTreatment;
import com.plot.plugin.earthwork.model.ExclusionZone;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.GradingZoneType;
import com.plot.plugin.earthwork.model.VerticalAdjustmentPolicy;
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
        enableFlexibleBalance(site);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_SITE_WIDE);
        site.getCompositionPolicy().setBalanceMethod(CompositionPolicy.BALANCE_METHOD_EARTHWORK_OPTIMIZATION);
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

        SiteEarthworkReport volumes = EarthworkOptimizationSolver.collectZoneVolumes(grid);
        assertEquals(0L, volumes.totals().geometricCutVolume());
        assertEquals(0L, volumes.totals().geometricFillVolume());
    }

    @Test
    void reportOnlyBalanceDoesNotMoveDesignElevations() {
        EarthworkSite site = adjacentCutFillZones(60, 72);
        enableFlexibleBalance(site);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_SITE_WIDE);
        site.getCompositionPolicy().setBalanceMethod(CompositionPolicy.BALANCE_METHOD_NONE);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(2, 5), 2, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(12, 5), 12, 5, 60),
            new TerrainSnapshot.Column(new Vec2d(15, 5), 15, 5, 60)
        ));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();

        assertEquals(60, grid.get(2, 5).targetY());
        assertEquals(60, grid.get(5, 5).targetY());
        assertEquals(72, grid.get(12, 5).targetY());
        assertEquals(72, grid.get(15, 5).targetY());
        assertEquals(0, site.getLastSiteWideVerticalOffset());
        assertTrue(site.getLastZoneVerticalOffsets().isEmpty());

        SiteEarthworkReport volumes = EarthworkOptimizationSolver.collectZoneVolumes(grid, site);
        EarthworkAllocationMatrix matrix = EarthworkAllocationMatrix.fromZoneReports(volumes.byZone(), site);
        assertFalse(matrix.isEmpty());
    }

    @Test
    void siteWideUniformOffsetBalancesFieldEndToEnd() {
        EarthworkSite site = adjacentCutFillZones(60, 68);
        enableFlexibleBalance(site);
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

        SiteEarthworkReport volumes = EarthworkOptimizationSolver.collectZoneVolumes(grid);
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
    void singleZoneAutoBalanceScoresCutFillAfterSlopes() {
        EarthworkSite naive = slopedSinglePad(false, 80);
        EarthworkSite auto = slopedSinglePad(true, 80);
        TerrainSnapshot terrain = slopedSinglePadTerrain();

        DesignTerrainGrid naiveGrid = DesignTerrainComposer.compose(naive, terrain, null).grid();
        DesignTerrainGrid autoGrid = DesignTerrainComposer.compose(auto, terrain, null).grid();

        long naiveImbalance = SlopeCoupledVerticalSearch.geometricCutFillImbalance(naiveGrid, naive);
        long autoImbalance = SlopeCoupledVerticalSearch.geometricCutFillImbalance(autoGrid, auto);

        assertTrue(autoImbalance <= naiveImbalance,
            () -> "auto imbalance=" + autoImbalance + " naive-pad-then-slope imbalance=" + naiveImbalance
                + " autoPadY=" + autoGrid.get(2, 5).targetY()
                + " naivePadY=" + naiveGrid.get(2, 5).targetY());
        assertNotEquals(naiveGrid.get(2, 5).targetY(), autoGrid.get(2, 5).targetY(),
            "slope volume should move the recommended pad height off the pad-only seed");
        assertTrue(autoGrid.get(2, 5).targetY() < 80);
    }

    @Test
    void siteWideBalanceDoesNotMoveDefaultRoadCorridor() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0), new Vec2d(30, 0), new Vec2d(30, 10), new Vec2d(0, 10)));

        GradingZone corridor = new GradingZone("corridor", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        corridor.setType(GradingZoneType.ROAD_CORRIDOR);
        corridor.setRoadEdgeRef("edge-main");
        corridor.getRegion().setAutoBalance(true);

        GradingZone landscape = new GradingZone("landscape", List.of(
            new Vec2d(10, 0), new Vec2d(30, 0), new Vec2d(30, 10), new Vec2d(10, 10)));
        landscape.getRegion().setAutoBalance(true);
        landscape.getRegion().setManualTargetElevation(80);

        site.addZone(corridor);
        site.addZone(landscape);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_SITE_WIDE);
        site.getCompositionPolicy().setBalanceMethod(CompositionPolicy.BALANCE_METHOD_UNIFORM);
        site.getCompositionPolicy().setBalanceResidualUniformPolish(false);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(20, 5), 20, 5, 60),
            new TerrainSnapshot.Column(new Vec2d(24, 5), 24, 5, 60)
        ));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(
            site,
            terrain,
            null,
            BuildingFootprintLookup.NONE,
            (RoadSurfaceLookup) (edgeId, point) -> 70).grid();
        assertEquals(70, grid.get(5, 5).targetY());
        assertFalse(corridor.isAutoAdjustElevation());
        assertTrue(grid.get(20, 5).targetY() < 80);
    }

    @Test
    void siteWideBalanceRebuildsSlopesAgainstAdjustedPad() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(-20, 0), new Vec2d(40, 0), new Vec2d(40, 10), new Vec2d(-20, 10)));

        GradingZone cut = new GradingZone("zone-cut", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        cut.getRegion().setAutoBalance(true);
        cut.getRegion().setManualTargetElevation(64);
        cut.getEdgeSettings().setDefaultTreatment(EdgeTreatment.CUT_FILL_SLOPE);
        cut.getEdgeSettings().setCutSlopePitchRatio(1);
        cut.getEdgeSettings().setMaximumReachBlocks(16);

        GradingZone fill = new GradingZone("zone-fill", List.of(
            new Vec2d(24, 0), new Vec2d(34, 0), new Vec2d(34, 10), new Vec2d(24, 10)));
        fill.getRegion().setAutoBalance(true);
        fill.getRegion().setManualTargetElevation(80);

        site.addZone(cut);
        site.addZone(fill);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_SITE_WIDE);
        site.getCompositionPolicy().setBalanceMethod(CompositionPolicy.BALANCE_METHOD_UNIFORM);
        site.getCompositionPolicy().setBalanceResidualUniformPolish(false);

        Vec2d exteriorCenter = new Vec2d(-2.5, 5.5);
        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(2, 5), 2, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(26, 5), 26, 5, 60),
            new TerrainSnapshot.Column(new Vec2d(30, 5), 30, 5, 60),
            new TerrainSnapshot.Column(exteriorCenter, -3, 5, 70)
        ));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();
        int padY = grid.get(2, 5).targetY();
        assertTrue(padY < 64, () -> "expected pad to drop for fill surplus, padY=" + padY
            + " fillY=" + grid.get(26, 5).targetY()
            + " uniform=" + site.getLastSiteWideVerticalOffset());

        int actualExterior = grid.get(-3, 5).targetY();
        assertNotEquals(70, actualExterior, () -> "slope cell should change with pad, padY=" + padY);
        int naiveShiftedGround = 70 + (padY - 64);
        assertNotEquals(naiveShiftedGround, actualExterior,
            () -> "slope must be rebuilt, not a uniform shift of existing ground; padY=" + padY
                + " exterior=" + actualExterior);
    }

    @Test
    void siteWideBalanceAfterSlopesBeatsPerZoneResidual() {
        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(2, 5), 2, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(12, 5), 12, 5, 60),
            new TerrainSnapshot.Column(new Vec2d(15, 5), 15, 5, 60),
            new TerrainSnapshot.Column(new Vec2d(22.5, 5.5), 22, 5, 60)
        ));

        EarthworkSite perZone = slopedFillSite();
        perZone.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_PER_ZONE);
        DesignTerrainGrid perZoneGrid = DesignTerrainComposer.compose(perZone, terrain, null).grid();
        SiteEarthworkReport perZoneVolumes = EarthworkOptimizationSolver.collectZoneVolumes(perZoneGrid, perZone);

        EarthworkSite siteWide = slopedFillSite();
        enableFlexibleBalance(siteWide);
        siteWide.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_SITE_WIDE);
        siteWide.getCompositionPolicy().setBalanceMethod(CompositionPolicy.BALANCE_METHOD_UNIFORM);
        DesignTerrainGrid siteWideGrid = DesignTerrainComposer.compose(siteWide, terrain, null).grid();
        SiteEarthworkReport siteWideVolumes = EarthworkOptimizationSolver.collectZoneVolumes(siteWideGrid, siteWide);

        double perZoneResidual = Math.abs(
            perZoneVolumes.totals().compactedFillSupply() - perZoneVolumes.totals().compactedFillDemand());
        double siteWideResidual = Math.abs(
            siteWideVolumes.totals().compactedFillSupply() - siteWideVolumes.totals().compactedFillDemand());
        assertTrue(siteWideResidual < perZoneResidual,
            () -> "site-wide residual=" + siteWideResidual + " per-zone residual=" + perZoneResidual);
    }

    @Test
    void siteWideBalanceDoesNotMoveBuildingPadOrManualElevation() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0), new Vec2d(30, 0), new Vec2d(30, 10), new Vec2d(0, 10)));

        GradingZone building = new GradingZone("building", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        building.setType(GradingZoneType.BUILDING_PAD);
        building.getDesignSurface().setElevation(70);

        GradingZone pit = new GradingZone("pit", List.of(
            new Vec2d(10, 0), new Vec2d(16, 0), new Vec2d(16, 10), new Vec2d(10, 10)));
        pit.setType(GradingZoneType.EXCAVATION_PIT);
        pit.getDesignSurface().setBottomElevation(55);

        GradingZone landscape = new GradingZone("landscape", List.of(
            new Vec2d(16, 0), new Vec2d(30, 0), new Vec2d(30, 10), new Vec2d(16, 10)));
        landscape.getRegion().setAutoBalance(true);
        landscape.getRegion().setManualTargetElevation(80);

        site.addZone(building);
        site.addZone(pit);
        site.addZone(landscape);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_SITE_WIDE);
        site.getCompositionPolicy().setBalanceMethod(CompositionPolicy.BALANCE_METHOD_UNIFORM);
        site.getCompositionPolicy().setBalanceResidualUniformPolish(false);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(12, 5), 12, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(20, 5), 20, 5, 60),
            new TerrainSnapshot.Column(new Vec2d(24, 5), 24, 5, 60)
        ));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();
        assertEquals(70, grid.get(5, 5).targetY());
        assertEquals(55, grid.get(12, 5).targetY());
        assertTrue(grid.get(20, 5).targetY() < 80);
        assertNotEquals(0, site.getLastSiteWideVerticalOffset());
    }

    @Test
    void siteWideBalanceDoesNotMoveManualFixedPad() {
        EarthworkSite site = adjacentCutFillZones(60, 80);
        site.getZone("zone-fill").getRegion().setAutoBalance(true);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_SITE_WIDE);
        site.getCompositionPolicy().setBalanceMethod(CompositionPolicy.BALANCE_METHOD_UNIFORM);
        site.getCompositionPolicy().setBalanceResidualUniformPolish(false);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(2, 5), 2, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(12, 5), 12, 5, 60),
            new TerrainSnapshot.Column(new Vec2d(15, 5), 15, 5, 60)
        ));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();
        assertEquals(60, grid.get(2, 5).targetY());
        assertTrue(grid.get(12, 5).targetY() < 80);
    }

    @Test
    void siteWideBalanceClampsBoundedVerticalPolicy() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0), new Vec2d(30, 0), new Vec2d(30, 10), new Vec2d(0, 10)));

        GradingZone corridor = new GradingZone("corridor", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        corridor.getRegion().setAutoBalance(true);
        corridor.getRegion().setManualTargetElevation(70);
        corridor.setVerticalAdjustmentPolicy(VerticalAdjustmentPolicy.bounded(1, 1.0f));

        GradingZone landscape = new GradingZone("landscape", List.of(
            new Vec2d(10, 0), new Vec2d(30, 0), new Vec2d(30, 10), new Vec2d(10, 10)));
        landscape.getRegion().setAutoBalance(true);
        landscape.getRegion().setManualTargetElevation(80);

        site.addZone(corridor);
        site.addZone(landscape);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_SITE_WIDE);
        site.getCompositionPolicy().setBalanceMethod(CompositionPolicy.BALANCE_METHOD_UNIFORM);
        site.getCompositionPolicy().setBalanceResidualUniformPolish(false);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(20, 5), 20, 5, 60),
            new TerrainSnapshot.Column(new Vec2d(24, 5), 24, 5, 60)
        ));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();
        int corridorY = grid.get(5, 5).targetY();
        assertTrue(corridorY >= 69 && corridorY <= 71,
            () -> "corridor should stay within ±1 of design 70, got " + corridorY);
        assertTrue(grid.get(20, 5).targetY() < 80);
        assertNotEquals(0, site.getLastSiteWideVerticalOffset());
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

    private static void enableFlexibleBalance(EarthworkSite site) {
        for (GradingZone zone : site.getGradingZones().values()) {
            zone.getRegion().setAutoBalance(true);
        }
    }

    private static EarthworkSite slopedFillSite() {
        EarthworkSite site = adjacentCutFillZones(60, 72);
        GradingZone fill = site.getZone("zone-fill");
        fill.getEdgeSettings().setDefaultTreatment(EdgeTreatment.CUT_FILL_SLOPE);
        fill.getEdgeSettings().setFillSlopePitchNumerator(1);
        fill.getEdgeSettings().setFillSlopePitchDenominator(1);
        fill.getEdgeSettings().setMaximumReachBlocks(12);
        return site;
    }

    private static EarthworkSite slopedSinglePad(boolean autoBalance, int seedElevation) {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(-16, 0), new Vec2d(24, 0), new Vec2d(24, 10), new Vec2d(-16, 10)));
        GradingZone pad = new GradingZone("pad", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        pad.getRegion().setAutoBalance(autoBalance);
        pad.getRegion().setManualTargetElevation(seedElevation);
        pad.getEdgeSettings().setDefaultTreatment(EdgeTreatment.CUT_FILL_SLOPE);
        pad.getEdgeSettings().setCutSlopePitchRatio(1);
        pad.getEdgeSettings().setFillSlopePitchNumerator(1);
        pad.getEdgeSettings().setFillSlopePitchDenominator(1);
        pad.getEdgeSettings().setMaximumReachBlocks(12);
        site.addZone(pad);
        return site;
    }

    private static TerrainSnapshot slopedSinglePadTerrain() {
        return TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(2, 5), 2, 5, 80),
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 80),
            new TerrainSnapshot.Column(new Vec2d(8, 5), 8, 5, 80),
            new TerrainSnapshot.Column(new Vec2d(-8.5, 5.5), -9, 5, 64),
            new TerrainSnapshot.Column(new Vec2d(-4.5, 5.5), -5, 5, 64),
            new TerrainSnapshot.Column(new Vec2d(14.5, 5.5), 14, 5, 64)
        ));
    }
}
