package com.plot.plugin.earthwork.solver;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.core.material.MaterialConversionModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按 {@link EarthworkAllocationMatrix} 对分区施加差异化竖向偏移（A→B 调配）。
 */
public final class ZoneAllocationBalanceAdjuster {

    private ZoneAllocationBalanceAdjuster() {
    }

    public record BalanceResult(
            Map<String, Integer> zoneOffsets,
            int residualUniformOffset) {
    }

    public static BalanceResult balance(
            DesignTerrainGrid grid,
            EarthworkSite site) {
        if (grid == null || site == null || site.getZoneCount() < 2) {
            return new BalanceResult(Map.of(), 0);
        }
        SiteEarthworkReport snapshot = collectZoneVolumes(grid, site);
        EarthworkAllocationMatrix matrix = EarthworkAllocationMatrix.fromZoneReports(
            snapshot.byZone(),
            site);
        Map<String, List<SiteWideBalanceAdjuster.CellSample>> samplesByZone = collectSamplesByZone(grid);
        Map<String, Integer> zoneOffsets = computeZoneOffsets(
            matrix,
            countCellsByZone(grid),
            samplesByZone,
            site.getMaterialModel());
        applyZoneOffsets(grid, zoneOffsets);

        int residual = 0;
        if (site.getCompositionPolicy().isBalanceResidualUniformPolish()) {
            residual = applyResidualUniformOffset(grid, site.getMaterialModel());
        }
        return new BalanceResult(zoneOffsets, residual);
    }

    public static Map<String, Integer> computeZoneOffsets(
            EarthworkAllocationMatrix matrix,
            Map<String, Integer> cellCountByZone) {
        return computeZoneOffsets(matrix, cellCountByZone, null, MaterialConversionModel.DEFAULT);
    }

    public static Map<String, Integer> computeZoneOffsets(
            EarthworkAllocationMatrix matrix,
            Map<String, Integer> cellCountByZone,
            Map<String, List<SiteWideBalanceAdjuster.CellSample>> samplesByZone,
            MaterialConversionModel materials) {
        if (matrix == null || matrix.isEmpty() || cellCountByZone == null || cellCountByZone.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> volumeIntent = new LinkedHashMap<>();
        for (EarthworkAllocationMatrix.Transfer transfer : matrix.transfers()) {
            if (transfer.volume() <= 0L) {
                continue;
            }
            if (transfer.isExport()) {
                addVolumeIntent(volumeIntent, transfer.sourceZoneId(), transfer.volume());
            } else if (transfer.isImport()) {
                addVolumeIntent(volumeIntent, transfer.destinationZoneId(), -transfer.volume());
            } else {
                addVolumeIntent(volumeIntent, transfer.sourceZoneId(), transfer.volume());
                addVolumeIntent(volumeIntent, transfer.destinationZoneId(), -transfer.volume());
            }
        }

        Map<String, Integer> offsets = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : volumeIntent.entrySet()) {
            String zoneId = entry.getKey();
            int cellCount = cellCountByZone.getOrDefault(zoneId, 0);
            if (cellCount <= 0) {
                continue;
            }
            long intent = entry.getValue();
            int offset;
            List<SiteWideBalanceAdjuster.CellSample> zoneSamples = samplesByZone != null
                ? samplesByZone.get(zoneId)
                : null;
            if (zoneSamples != null && !zoneSamples.isEmpty()) {
                offset = WeightedBalanceSolver.findVerticalOffsetForVolumeIntent(
                    zoneSamples, intent, materials);
            } else {
                offset = (int) Math.round(intent / (double) cellCount);
            }
            if (offset != 0) {
                offsets.put(zoneId, offset);
            }
        }
        return Map.copyOf(offsets);
    }

    public static Map<String, List<SiteWideBalanceAdjuster.CellSample>> collectSamplesByZone(
            DesignTerrainGrid grid) {
        Map<String, List<SiteWideBalanceAdjuster.CellSample>> samplesByZone = new LinkedHashMap<>();
        if (grid == null) {
            return samplesByZone;
        }
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (!cell.participatesInEarthwork() || cell.zoneId() == null || cell.zoneId().isBlank()) {
                continue;
            }
            samplesByZone
                .computeIfAbsent(cell.zoneId(), ignored -> new ArrayList<>())
                .add(new SiteWideBalanceAdjuster.CellSample(cell.existingGroundY(), cell.targetY()));
        }
        return samplesByZone;
    }

    public static void applyZoneOffsets(DesignTerrainGrid grid, Map<String, Integer> zoneOffsets) {
        if (grid == null || zoneOffsets == null || zoneOffsets.isEmpty()) {
            return;
        }
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (!cell.participatesInEarthwork() || cell.zoneId() == null || cell.zoneId().isBlank()) {
                continue;
            }
            Integer offset = zoneOffsets.get(cell.zoneId());
            if (offset != null && offset != 0) {
                cell.setTargetY(cell.targetY() + offset);
            }
        }
    }

    public static SiteEarthworkReport collectZoneVolumes(DesignTerrainGrid grid) {
        return collectZoneVolumes(grid, null);
    }

    public static SiteEarthworkReport collectZoneVolumes(DesignTerrainGrid grid, EarthworkSite site) {
        Map<String, SiteEarthworkReport.VolumeMetrics> zoneMetrics = new LinkedHashMap<>();
        SiteEarthworkReport.VolumeMetrics totals = new SiteEarthworkReport.VolumeMetrics();
        if (grid == null) {
            return SiteEarthworkReport.fromMetrics(site, totals, zoneMetrics);
        }
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (!cell.participatesInEarthwork() || cell.zoneId() == null || cell.zoneId().isBlank()) {
                continue;
            }
            SiteEarthworkReport.VolumeMetrics zoneVolume = zoneMetrics.computeIfAbsent(
                cell.zoneId(),
                ignored -> new SiteEarthworkReport.VolumeMetrics());
            int groundY = cell.existingGroundY();
            int targetY = cell.targetY();
            if (groundY > targetY) {
                long cut = groundY - targetY;
                totals.addCut(cut, 0L);
                zoneVolume.addCut(cut, 0L);
            } else if (groundY < targetY) {
                long fill = targetY - groundY;
                totals.addFill(fill, 0L);
                zoneVolume.addFill(fill, 0L);
            }
        }
        return SiteEarthworkReport.fromMetrics(site, totals, zoneMetrics);
    }

    public static Map<String, Integer> countCellsByZone(DesignTerrainGrid grid) {
        Map<String, Integer> counts = new HashMap<>();
        if (grid == null) {
            return counts;
        }
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (!cell.participatesInEarthwork() || cell.zoneId() == null || cell.zoneId().isBlank()) {
                continue;
            }
            counts.merge(cell.zoneId(), 1, Integer::sum);
        }
        return counts;
    }

    private static int applyResidualUniformOffset(
            DesignTerrainGrid grid,
            MaterialConversionModel materials) {
        java.util.List<SiteWideBalanceAdjuster.CellSample> samples = new java.util.ArrayList<>();
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (!cell.participatesInEarthwork() || cell.zoneId() == null || cell.zoneId().isBlank()) {
                continue;
            }
            samples.add(new SiteWideBalanceAdjuster.CellSample(cell.existingGroundY(), cell.targetY()));
        }
        if (samples.isEmpty()) {
            return 0;
        }
        int offset = SiteWideBalanceAdjuster.findBalancedVerticalOffset(samples, materials);
        SiteWideBalanceAdjuster.applyOffset(grid, offset);
        return offset;
    }

    private static void addVolumeIntent(Map<String, Long> volumeIntent, String zoneId, long delta) {
        if (zoneId == null || zoneId.isBlank() || delta == 0L) {
            return;
        }
        volumeIntent.merge(zoneId, delta, Long::sum);
    }
}
