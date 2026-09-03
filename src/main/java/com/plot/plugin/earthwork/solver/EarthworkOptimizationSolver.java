package com.plot.plugin.earthwork.solver;

import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mode B — 土方竖向优化求解器。
 * <p>
 * 在 {@link com.plot.plugin.earthwork.model.VerticalAdjustmentPolicy} 约束下，
 * 调整可调分区标高以减少外运/外进。与 {@link EarthworkAllocationMatrix}（Mode A：仅报告土方怎么搬）
 * 不同，本求解器会提出分区 ΔY / 统一 ΔY。
 */
public final class EarthworkOptimizationSolver {

    private EarthworkOptimizationSolver() {
    }

    public record BalanceResult(
            Map<String, Integer> zoneOffsets,
            int residualUniformOffset) {
        public boolean isZero() {
            if (residualUniformOffset != 0) {
                return false;
            }
            if (zoneOffsets == null || zoneOffsets.isEmpty()) {
                return true;
            }
            for (int value : zoneOffsets.values()) {
                if (value != 0) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * 根据当前设计面方量提出分区 ΔY / 残余统一 ΔY，不修改网格。
     */
    public static BalanceResult propose(
            DesignTerrainGrid grid,
            EarthworkSite site) {
        return propose(grid, site, null);
    }

    /**
     * @param resolvedSurfaces 可选；有则按 {@link com.plot.plugin.earthwork.design.ResolvedDesignSurface#isSolverVariable()}
     *                         划分可调分区，避免把 LOCKED/DERIVED/引用失败面当成同质变量。
     */
    /**
     * 仅提出分区 ΔY，不做残余统一抛光（抛光应由边坡耦合离散搜索完成）。
     */
    public static BalanceResult proposeZoneOffsetsOnly(
            DesignTerrainGrid grid,
            EarthworkSite site,
            Map<String, com.plot.plugin.earthwork.design.ResolvedDesignSurface> resolvedSurfaces) {
        if (grid == null || site == null || site.getZoneCount() < 2) {
            return new BalanceResult(Map.of(), 0);
        }
        SiteEarthworkReport snapshot = collectZoneVolumes(grid, site);
        Map<String, EarthworkVolumeReport> flexibleByZone =
            flexibleZoneReports(snapshot.byZone(), site, resolvedSurfaces);
        if (flexibleByZone.isEmpty()) {
            return new BalanceResult(Map.of(), 0);
        }
        EarthworkAllocationMatrix matrix = EarthworkAllocationMatrix.fromZoneReports(
            flexibleByZone,
            site);
        Map<String, Integer> zoneOffsets = computeZoneOffsets(
            matrix,
            countFlexibleCellsByZone(grid, site, resolvedSurfaces),
            collectFlexibleSamplesByZone(grid, site, resolvedSurfaces),
            site,
            flexibleByZone);
        return new BalanceResult(zoneOffsets, 0);
    }

    public static BalanceResult propose(
            DesignTerrainGrid grid,
            EarthworkSite site,
            Map<String, com.plot.plugin.earthwork.design.ResolvedDesignSurface> resolvedSurfaces) {
        BalanceResult zoneOnly = proposeZoneOffsetsOnly(grid, site, resolvedSurfaces);
        int residual = 0;
        if (site != null && site.getCompositionPolicy().isBalanceResidualUniformPolish()) {
            // 线性残差估计仅作无坡面场景的快速启发；合成主路径用 SlopeCoupledVerticalSearch。
            residual = proposeResidualUniformOffset(
                grid, site, zoneOnly.zoneOffsets(), resolvedSurfaces);
        }
        return new BalanceResult(zoneOnly.zoneOffsets(), residual);
    }

    public static BalanceResult balance(
            DesignTerrainGrid grid,
            EarthworkSite site) {
        BalanceResult result = propose(grid, site);
        applyZoneOffsets(grid, result.zoneOffsets());
        if (result.residualUniformOffset() != 0) {
            SiteWideBalanceAdjuster.applyOffset(grid, result.residualUniformOffset(), site);
        }
        return result;
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

    private static Map<String, EarthworkVolumeReport> flexibleZoneReports(
            Map<String, EarthworkVolumeReport> byZone,
            EarthworkSite site,
            Map<String, com.plot.plugin.earthwork.design.ResolvedDesignSurface> resolvedSurfaces) {
        if (byZone == null || byZone.isEmpty()) {
            return Map.of();
        }
        Map<String, EarthworkVolumeReport> flexible = new LinkedHashMap<>();
        for (Map.Entry<String, EarthworkVolumeReport> entry : byZone.entrySet()) {
            if (!isSolverVariable(site, resolvedSurfaces, entry.getKey())) {
                continue;
            }
            flexible.put(entry.getKey(), entry.getValue());
        }
        return flexible;
    }

    private static boolean isSolverVariable(
            EarthworkSite site,
            Map<String, com.plot.plugin.earthwork.design.ResolvedDesignSurface> resolvedSurfaces,
            String zoneId) {
        if (resolvedSurfaces != null) {
            com.plot.plugin.earthwork.design.ResolvedDesignSurface resolved = resolvedSurfaces.get(zoneId);
            if (resolved != null) {
                return resolved.isSolverVariable();
            }
        }
        return site == null || !site.isElevationLocked(zoneId);
    }

    private static Map<String, Integer> countFlexibleCellsByZone(
            DesignTerrainGrid grid,
            EarthworkSite site,
            Map<String, com.plot.plugin.earthwork.design.ResolvedDesignSurface> resolvedSurfaces) {
        Map<String, Integer> counts = new HashMap<>();
        if (grid == null) {
            return counts;
        }
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (!cell.participatesInEarthwork() || cell.zoneId() == null || cell.zoneId().isBlank()) {
                continue;
            }
            if (!isSolverVariable(site, resolvedSurfaces, cell.zoneId())) {
                continue;
            }
            counts.merge(cell.zoneId(), 1, Integer::sum);
        }
        return counts;
    }

    private static Map<String, List<SiteWideBalanceAdjuster.CellSample>> collectFlexibleSamplesByZone(
            DesignTerrainGrid grid,
            EarthworkSite site,
            Map<String, com.plot.plugin.earthwork.design.ResolvedDesignSurface> resolvedSurfaces) {
        Map<String, List<SiteWideBalanceAdjuster.CellSample>> samplesByZone = new LinkedHashMap<>();
        if (grid == null) {
            return samplesByZone;
        }
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (!cell.participatesInEarthwork() || cell.zoneId() == null || cell.zoneId().isBlank()) {
                continue;
            }
            if (!isSolverVariable(site, resolvedSurfaces, cell.zoneId())) {
                continue;
            }
            samplesByZone
                .computeIfAbsent(cell.zoneId(), ignored -> new ArrayList<>())
                .add(new SiteWideBalanceAdjuster.CellSample(cell.existingGroundY(), cell.targetY()));
        }
        return samplesByZone;
    }

    private static int proposeResidualUniformOffset(
            DesignTerrainGrid grid,
            EarthworkSite site,
            Map<String, Integer> zoneOffsets,
            Map<String, com.plot.plugin.earthwork.design.ResolvedDesignSurface> resolvedSurfaces) {
        List<SiteWideBalanceAdjuster.CellSample> flexible = new ArrayList<>();
        List<SiteWideBalanceAdjuster.CellSample> locked = new ArrayList<>();
        SiteWideBalanceAdjuster.splitSamples(grid, site, zoneOffsets, flexible, locked, resolvedSurfaces);
        return SiteWideBalanceAdjuster.findBalancedVerticalOffset(
            flexible,
            locked,
            site != null ? site.getMaterialModel() : MaterialConversionModel.DEFAULT);
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
