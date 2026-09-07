package com.plot.plugin.earthwork;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.grading.ZoneTargetElevationStats;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;
import com.plot.api.geometry.Vec2d;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoneTargetElevationStatsTest {

    @Test
    void statsByZoneIdReflectsPerZoneTargets() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        putCell(grid, 1, 1, "pad", 64);
        putCell(grid, 2, 2, "pad", 66);
        putCell(grid, 8, 8, "pit", 40);
        putCell(grid, 9, 9, "road", 67);

        Map<String, ZoneTargetElevationStats> stats = ZoneTargetElevationStats.fromGrid(grid);

        assertEquals(64, stats.get("pad").min());
        assertEquals(66, stats.get("pad").max());
        assertEquals(65, stats.get("pad").representative());
        assertEquals(40, stats.get("pit").min());
        assertEquals(40, stats.get("pit").max());
        assertEquals(67, stats.get("road").representative());
    }

    @Test
    void applyZoneLastReportsWritesPerZoneElevationNotSiteAggregate() {
        EarthworkSite site = new EarthworkSite();
        GradingZone pad = new GradingZone("pad", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        GradingZone pit = new GradingZone("pit", List.of(
            new Vec2d(10, 0), new Vec2d(20, 0), new Vec2d(20, 10), new Vec2d(10, 10)));
        site.addZone(pad);
        site.addZone(pit);

        DesignTerrainGrid grid = new DesignTerrainGrid();
        putCell(grid, 5, 5, "pad", 64);
        putCell(grid, 15, 5, "pit", 40);

        EarthworkGenerationResult result = new EarthworkGenerationResult();
        result.designTerrainGrid = grid;
        result.resolvedElevationMin = 40;
        result.resolvedElevationMax = 64;
        result.resolvedElevation = 52;
        result.siteVolumeReport = SiteEarthworkReport.empty();

        Map<String, ZoneTargetElevationStats> elevationByZone = ZoneTargetElevationStats.fromGrid(grid);
        for (GradingZone zone : site.getGradingZones().values()) {
            ZoneTargetElevationStats stats = elevationByZone.get(zone.getId());
            if (stats != null) {
                zone.getRegion().setLastResolvedElevation(stats.representative());
                zone.getRegion().setLastResolvedElevationMin(stats.min());
                zone.getRegion().setLastResolvedElevationMax(stats.max());
            }
        }

        assertEquals(64, pad.getRegion().getLastResolvedElevation());
        assertEquals(64, pad.getRegion().getLastResolvedElevationMin());
        assertEquals(64, pad.getRegion().getLastResolvedElevationMax());
        assertEquals(40, pit.getRegion().getLastResolvedElevation());
        assertEquals(40, pit.getRegion().getLastResolvedElevationMin());
        assertEquals(40, pit.getRegion().getLastResolvedElevationMax());
    }

    @Test
    void structureBlocksAreExcludedFromFillChangedCounts() {
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        result.placementRecords.put(new BlockPos(0, 60, 0), new BlockRecord(new BlockPos(0, 60, 0), "a", "b"));
        result.changeTypes.put(new BlockPos(0, 60, 0), EarthworkGenerationResult.ChangeType.CUT);
        result.placementRecords.put(new BlockPos(1, 60, 0), new BlockRecord(new BlockPos(1, 60, 0), "a", "b"));
        result.changeTypes.put(new BlockPos(1, 60, 0), EarthworkGenerationResult.ChangeType.FILL);
        result.placementRecords.put(new BlockPos(2, 60, 0), new BlockRecord(new BlockPos(2, 60, 0), "a", "b"));
        result.changeTypes.put(new BlockPos(2, 60, 0), EarthworkGenerationResult.ChangeType.STRUCTURE);
        result.volumeReport = com.plot.plugin.earthwork.volume.EarthworkVolumeReport.fromMetrics(
            10, 10, null, 0, 0);

        result.syncChangedBlocksFromPlacements();

        assertEquals(1L, result.volumeReport.cutChangedBlocks());
        assertEquals(1L, result.volumeReport.fillChangedBlocks());
    }

    private static void putCell(DesignTerrainGrid grid, int x, int z, String zoneId, int targetY) {
        DesignTerrainCell cell = new DesignTerrainCell(x, z, new Vec2d(x, z), targetY - 2);
        cell.setZoneId(zoneId);
        cell.setTargetY(targetY);
        grid.put(x, z, cell);
    }
}
