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
            10, 10, 0, 0);

        result.syncChangedBlocksFromPlacements();

        assertEquals(1L, result.volumeReport.cutChangedBlocks());
        assertEquals(1L, result.volumeReport.fillChangedBlocks());
    }

    @Test
    void syncChangedBlocksReaggregatesPerZoneFromPlacements() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        putCell(grid, 0, 0, "zone-a", 65);
        putCell(grid, 1, 0, "zone-b", 65);

        var zoneA = com.plot.plugin.earthwork.volume.EarthworkVolumeReport.fromMetrics(5, 0, 99, 88);
        var zoneB = com.plot.plugin.earthwork.volume.EarthworkVolumeReport.fromMetrics(0, 5, 77, 66);
        var totals = com.plot.plugin.earthwork.volume.EarthworkVolumeReport.fromMetrics(5, 5, 50, 50);

        EarthworkGenerationResult result = new EarthworkGenerationResult();
        result.designTerrainGrid = grid;
        result.volumeReport = totals;
        result.siteVolumeReport = new SiteEarthworkReport(totals, Map.of("zone-a", zoneA, "zone-b", zoneB));
        putPlacement(result, new BlockPos(0, 60, 0), EarthworkGenerationResult.ChangeType.CUT, "zone-a");
        putPlacement(result, new BlockPos(1, 60, 0), EarthworkGenerationResult.ChangeType.FILL, "zone-b");
        putPlacement(result, new BlockPos(0, 61, 0), EarthworkGenerationResult.ChangeType.STRUCTURE, "zone-a");

        result.syncChangedBlocksFromPlacements();

        assertEquals(1L, result.volumeReport.cutChangedBlocks());
        assertEquals(1L, result.volumeReport.fillChangedBlocks());
        assertEquals(1L, result.siteVolumeReport.totals().cutChangedBlocks());
        assertEquals(1L, result.siteVolumeReport.totals().fillChangedBlocks());
        assertEquals(1L, result.siteVolumeReport.zoneReport("zone-a").cutChangedBlocks());
        assertEquals(0L, result.siteVolumeReport.zoneReport("zone-a").fillChangedBlocks());
        assertEquals(0L, result.siteVolumeReport.zoneReport("zone-b").cutChangedBlocks());
        assertEquals(1L, result.siteVolumeReport.zoneReport("zone-b").fillChangedBlocks());
        assertEquals(5L, result.siteVolumeReport.zoneReport("zone-a").geometricCutVolume());
        assertEquals(5L, result.siteVolumeReport.zoneReport("zone-b").geometricFillVolume());
        assertEquals(1L, result.structureChangedBlocks);
        assertEquals(0L, result.unassignedCutChangedBlocks);
    }

    @Test
    void syncTracksUnassignedPlacementsSeparatelyFromZones() {
        var totals = com.plot.plugin.earthwork.volume.EarthworkVolumeReport.fromMetrics(3, 2, 0, 0);
        var zoneA = com.plot.plugin.earthwork.volume.EarthworkVolumeReport.fromMetrics(3, 0, 0, 0);
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        result.volumeReport = totals;
        result.siteVolumeReport = new SiteEarthworkReport(totals, Map.of("zone-a", zoneA));
        putPlacement(result, new BlockPos(0, 60, 0), EarthworkGenerationResult.ChangeType.CUT, "zone-a");
        putPlacement(result, new BlockPos(9, 60, 0), EarthworkGenerationResult.ChangeType.CUT, null);
        putPlacement(result, new BlockPos(9, 61, 0), EarthworkGenerationResult.ChangeType.FILL, "");

        result.syncChangedBlocksFromPlacements();

        assertEquals(2L, result.volumeReport.cutChangedBlocks());
        assertEquals(1L, result.volumeReport.fillChangedBlocks());
        assertEquals(1L, result.siteVolumeReport.zoneReport("zone-a").cutChangedBlocks());
        assertEquals(1L, result.unassignedCutChangedBlocks);
        assertEquals(1L, result.unassignedFillChangedBlocks);
        assertEquals(1L, result.siteVolumeReport.unassignedCutChangedBlocks());
        assertEquals(1L, result.siteVolumeReport.unassignedFillChangedBlocks());
        // 全场 = 分区之和 + 未归属
        assertEquals(
            result.siteVolumeReport.zoneReport("zone-a").cutChangedBlocks()
                + result.unassignedCutChangedBlocks,
            result.volumeReport.cutChangedBlocks());
    }

    @Test
    void explicitUnassignedMetaIsNotOverriddenByDesignGrid() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        putCell(grid, 9, 0, "zone-grid", 65);

        var totals = com.plot.plugin.earthwork.volume.EarthworkVolumeReport.fromMetrics(1, 0, 0, 0);
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        result.designTerrainGrid = grid;
        result.volumeReport = totals;
        result.siteVolumeReport = new SiteEarthworkReport(totals, Map.of(
            "zone-grid",
            com.plot.plugin.earthwork.volume.EarthworkVolumeReport.fromMetrics(1, 0, 0, 0)));
        // 明确 UNASSIGNED，即使网格在 (9,0) 有 zone-grid
        putPlacement(result, new BlockPos(9, 60, 0), EarthworkGenerationResult.ChangeType.CUT, "");

        result.syncChangedBlocksFromPlacements();

        assertEquals(1L, result.unassignedCutChangedBlocks);
        assertEquals(0L, result.siteVolumeReport.zoneReport("zone-grid").cutChangedBlocks());
        assertEquals(1L, result.volumeReport.cutChangedBlocks());
    }

    private static void putPlacement(
            EarthworkGenerationResult result,
            BlockPos pos,
            EarthworkGenerationResult.ChangeType type,
            String zoneId) {
        result.placementRecords.put(pos, new BlockRecord(pos, "a", "b"));
        result.changeTypes.put(pos, type);
        result.placementMeta.put(pos, com.plot.plugin.earthwork.pipeline.PlacementMeta.ofZone(zoneId));
    }

    private static void putCell(DesignTerrainGrid grid, int x, int z, String zoneId, int targetY) {
        DesignTerrainCell cell = new DesignTerrainCell(x, z, new Vec2d(x, z), targetY - 2);
        cell.setZoneId(zoneId);
        cell.setTargetY(targetY);
        grid.put(x, z, cell);
    }
}
