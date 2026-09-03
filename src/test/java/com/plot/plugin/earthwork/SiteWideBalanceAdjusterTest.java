package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.design.GradingSurfaceResolver;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.solver.EarthworkBalanceUtils;
import com.plot.plugin.earthwork.solver.SiteWideBalanceAdjuster;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.GradingRegion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteWideBalanceAdjusterTest {

    @Test
    void verticalOffsetBalancesResidualField() {
        List<SiteWideBalanceAdjuster.CellSample> samples = List.of(
            new SiteWideBalanceAdjuster.CellSample(70, 60),
            new SiteWideBalanceAdjuster.CellSample(68, 60),
            new SiteWideBalanceAdjuster.CellSample(55, 60),
            new SiteWideBalanceAdjuster.CellSample(53, 60));
        int offset = SiteWideBalanceAdjuster.findBalancedVerticalOffset(
            samples,
            new com.plot.core.material.MaterialConversionModel(1.0f, 1.0f));
        assertTrue(Math.abs(EarthworkBalanceUtils.computeBalanceDiff(
            samples.stream().map(s -> s.existingGroundY() - s.rawDesignTargetY()).toList(),
            offset,
            new com.plot.core.material.MaterialConversionModel(1.0f, 1.0f))) <= 3L);
    }

    @Test
    void applyOffsetShiftsParticipatingCells() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        DesignTerrainCell cell = new DesignTerrainCell(1, 1, new Vec2d(1, 1), 64);
        cell.setTargetY(60);
        cell.setZoneId("z1");
        grid.put(1, 1, cell);

        SiteWideBalanceAdjuster.applyOffset(grid, 3);
        assertEquals(63, grid.get(1, 1).targetY());
    }

    @Test
    void applyOffsetSkipsElevationLockedZones() {
        com.plot.plugin.earthwork.model.EarthworkSite site = new com.plot.plugin.earthwork.model.EarthworkSite();
        com.plot.plugin.earthwork.model.GradingZone pad = new com.plot.plugin.earthwork.model.GradingZone(
            "pad",
            List.of(new Vec2d(0, 0), new Vec2d(4, 0), new Vec2d(4, 4), new Vec2d(0, 4)));
        pad.setType(com.plot.plugin.earthwork.model.GradingZoneType.BUILDING_PAD);
        site.addZone(pad);

        DesignTerrainGrid grid = new DesignTerrainGrid();
        DesignTerrainCell cell = new DesignTerrainCell(1, 1, new Vec2d(1, 1), 64);
        cell.setTargetY(70);
        cell.setZoneId("pad");
        grid.put(1, 1, cell);

        SiteWideBalanceAdjuster.applyOffset(grid, -2, site);
        assertEquals(70, grid.get(1, 1).targetY());
    }

    @Test
    void applyOffsetClampsBoundedAndWeightsUniform() {
        com.plot.plugin.earthwork.model.EarthworkSite site = new com.plot.plugin.earthwork.model.EarthworkSite();
        com.plot.plugin.earthwork.model.GradingZone road = new com.plot.plugin.earthwork.model.GradingZone(
            "road",
            List.of(new Vec2d(0, 0), new Vec2d(4, 0), new Vec2d(4, 4), new Vec2d(0, 4)));
        road.setVerticalAdjustmentPolicy(
            com.plot.plugin.earthwork.model.VerticalAdjustmentPolicy.bounded(1, 1.0f));
        com.plot.plugin.earthwork.model.GradingZone landscape = new com.plot.plugin.earthwork.model.GradingZone(
            "landscape",
            List.of(new Vec2d(4, 0), new Vec2d(8, 0), new Vec2d(8, 4), new Vec2d(4, 4)));
        landscape.setVerticalAdjustmentPolicy(
            com.plot.plugin.earthwork.model.VerticalAdjustmentPolicy.adjustable(3, 0.5f));
        site.addZone(road);
        site.addZone(landscape);

        DesignTerrainGrid grid = new DesignTerrainGrid();
        DesignTerrainCell roadCell = new DesignTerrainCell(1, 1, new Vec2d(1, 1), 64);
        roadCell.setTargetY(70);
        roadCell.setZoneId("road");
        DesignTerrainCell landscapeCell = new DesignTerrainCell(5, 1, new Vec2d(5, 1), 64);
        landscapeCell.setTargetY(70);
        landscapeCell.setZoneId("landscape");
        grid.put(1, 1, roadCell);
        grid.put(5, 1, landscapeCell);

        SiteWideBalanceAdjuster.applyOffset(grid, 4, site);
        assertEquals(71, grid.get(1, 1).targetY());
        assertEquals(72, grid.get(5, 1).targetY());
    }

    @Test
    void deferBalanceUsesAverageGroundForFlatZones() {
        GradingRegion region = new GradingRegion(List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        region.setAutoBalance(true);
        List<Vec2d> centers = List.of(new Vec2d(2, 2), new Vec2d(8, 8));
        List<Integer> heights = List.of(60, 80);

        GradingSurfaceResolver.ResolvedSurface perZone = GradingSurfaceResolver.resolve(
            region, centers, heights, null, false);
        GradingSurfaceResolver.ResolvedSurface siteWide = GradingSurfaceResolver.resolve(
            region, centers, heights, null, true);

        assertEquals(70, siteWide.plane().evaluateAt(0, 0));
        assertTrue(Math.abs(perZone.plane().evaluateAt(0, 0) - 70) > 0
            || perZone.plane().evaluateAt(0, 0) == 70);
    }

    @Test
    void siteWideAutoBalanceKeepsManualSeedElevation() {
        GradingRegion region = new GradingRegion(List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        region.setAutoBalance(true);
        region.setManualTargetElevation(62);
        List<Vec2d> centers = List.of(new Vec2d(2, 2), new Vec2d(8, 8));
        List<Integer> heights = List.of(60, 80);

        GradingSurfaceResolver.ResolvedSurface siteWide = GradingSurfaceResolver.resolve(
            region, centers, heights, null, true);
        assertEquals(62, siteWide.plane().evaluateAt(0, 0));
    }
}
