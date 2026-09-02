package com.plot.plugin.earthwork;

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
            new com.plot.plugin.earthwork.model.EarthMaterialProperties(1.0f, 1.0f));
        assertTrue(Math.abs(EarthworkBalanceUtils.computeBalanceDiff(
            samples.stream().map(s -> s.existingGroundY() - s.rawDesignTargetY()).toList(),
            offset,
            new com.plot.plugin.earthwork.model.EarthMaterialProperties(1.0f, 1.0f))) <= 3L);
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
}
