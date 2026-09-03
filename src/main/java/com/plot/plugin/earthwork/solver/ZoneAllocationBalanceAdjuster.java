package com.plot.plugin.earthwork.solver;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
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
            site,
            snapshot.byZone());
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
        return computeZoneOffsets(matrix, cellCountByZone, null, null, null);
    }

    public static Map<String, Integer> computeZoneOffsets(
            EarthworkAllocationMatrix matrix,
            Map<String, Integer> cellCountByZone,
            Map<String, List<SiteWideBalanceAdjuster.CellSample>> samplesByZone,
            MaterialConversionModel materials) {
        return computeZoneOffsets(matrix, cellCountByZone, samplesByZone, null, null, materials);
    }

    public static Map<String, Integer> computeZoneOffsets(
            EarthworkAllocationMatrix matrix,
            Map<String, Integer> cellCountByZone,
            Map<String, List<SiteWideBalanceAdjuster.CellSample>> samplesByZone,
            EarthworkSite site,
            Map<String, EarthworkVolumeReport> byZone) {
        return computeZoneOffsets(
            matrix,
            cellCountByZone,
            samplesByZone,
            site,
            byZone,
            site != null ? site.getMaterialModel() : MaterialConversionModel.DEFAULT);
    }

    public static Map<String, Integer> computeZoneOffsets(
            EarthworkAllocationMatrix matrix,
            Map<String, Integer> cellCountByZone,
            Map<String, List<SiteWideBalanceAdjuster.CellSample>> samplesByZone,
            EarthworkSite site,
            Map<String, EarthworkVolumeReport> byZone,
            MaterialConversionModel fallbackMaterials) {
        if (matrix == null || matrix.isEmpty() || cellCountByZone == null || cellCountByZone.isEmpty()) {
            return Map.of();
        }
        MaterialConversionModel safeFallback = fallbackMaterials != null
            ? fallbackMaterials
            : MaterialConversionModel.DEFAULT;
        Map<String, Long> volumeIntent = new LinkedHashMap<>();
        for (EarthworkAllocationMatrix.Transfer transfer : matrix.transfers()) {
            if (transfer.volume() <= 0L) {
                continue;
            }
            if (transfer.isExport()) {
                long geometric = toGeometricExportIntent(
                    transfer.sourceZoneId(),
                    transfer.volume(),
                    site,
                    byZone,
                    safeFallback);
                addVolumeIntent(volumeIntent, transfer.sourceZoneId(), geometric);
            } else if (transfer.isImport()) {
                addVolumeIntent(volumeIntent, transfer.destinationZoneId(), -transfer.volume());
            } else {
                long sourceGeometric = toGeometricExportIntent(
                    transfer.sourceZoneId(),
                    transfer.volume(),
                    site,
                    byZone,
                    safeFallback);
                addVolumeIntent(volumeIntent, transfer.sourceZoneId(), sourceGeometric);
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
            MaterialConversionModel zoneMaterials = resolveZoneMaterials(
                site,
                zoneId,
                safeFallback);
            if (zoneSamples != null && !zoneSamples.isEmpty()) {
                offset = WeightedBalanceSolver.findVerticalOffsetForVolumeIntent(
                    zoneSamples, intent, zoneMaterials);
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

    private static long toGeometricExportIntent(
            String zoneId,
            long compactedFill,
            EarthworkSite site,
            Map<String, EarthworkVolumeReport> byZone,
            MaterialConversionModel fallback) {
        EarthworkVolumeReport report = byZone != null ? byZone.get(zoneId) : null;
        if (report != null) {
            long geometric = report.geometricCutForCompactedTransfer(compactedFill);
            if (geometric > 0L) {
                return geometric;
            }
        }
        return resolveZoneMaterials(site, zoneId, fallback).geometricCutForCompactedFill(compactedFill);
    }

    private static MaterialConversionModel resolveZoneMaterials(
            EarthworkSite site,
            String zoneId,
            MaterialConversionModel fallback) {
        if (site == null || zoneId == null || zoneId.isBlank()) {
            return fallback != null ? fallback : MaterialConversionModel.DEFAULT;
        }
        GradingZone zone = site.getZone(zoneId);
        MaterialConversionModel siteDefault = site.getMaterialModel() != null
            ? site.getMaterialModel()
            : MaterialConversionModel.DEFAULT;
        if (zone == null) {
            return siteDefault;
        }
        return zone.resolveMaterialModel(siteDefault);
    }
}
