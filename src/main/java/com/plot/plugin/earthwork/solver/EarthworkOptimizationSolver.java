package com.plot.plugin.earthwork.solver;

import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 土方竖向优化求解器：按分区几何挖填净差提出 ΔY，再由边坡耦合搜索校验。
 * <p>
 * 计量为 Minecraft 方块 1:1，不经材料调配矩阵。
 */
public final class EarthworkOptimizationSolver {

    /** 粗略几何偏移建议的绝对值上限（最终仍由边坡耦合搜索收敛）。 */
    public static final int MAX_GEOMETRIC_ZONE_OFFSET = 64;

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
        Map<String, Integer> zoneOffsets = computeGeometricZoneOffsets(
            flexibleByZone,
            countFlexibleCellsByZone(grid, site, resolvedSurfaces),
            collectFlexibleSamplesByZone(grid, site, resolvedSurfaces));
        return new BalanceResult(zoneOffsets, 0);
    }

    /**
     * 按分区几何挖填净差提出竖向偏移：挖多（cut−fill&gt;0）抬升，填多则降低。
     * 结果钳制在 {@link #MAX_GEOMETRIC_ZONE_OFFSET} 内，仅作候选建议。
     */
    public static Map<String, Integer> computeGeometricZoneOffsets(
            Map<String, EarthworkVolumeReport> byZone,
            Map<String, Integer> cellCountByZone,
            Map<String, List<SiteWideBalanceAdjuster.CellSample>> samplesByZone) {
        if (byZone == null || byZone.isEmpty() || cellCountByZone == null || cellCountByZone.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> offsets = new LinkedHashMap<>();
        for (Map.Entry<String, EarthworkVolumeReport> entry : byZone.entrySet()) {
            String zoneId = entry.getKey();
            EarthworkVolumeReport report = entry.getValue();
            if (report == null) {
                continue;
            }
            int cellCount = cellCountByZone.getOrDefault(zoneId, 0);
            if (cellCount <= 0) {
                continue;
            }
            long intent = report.netGeometricVolume();
            if (intent == 0L) {
                continue;
            }
            int offset;
            List<SiteWideBalanceAdjuster.CellSample> zoneSamples = samplesByZone != null
                ? samplesByZone.get(zoneId)
                : null;
            if (zoneSamples != null && !zoneSamples.isEmpty()) {
                offset = WeightedBalanceSolver.findVerticalOffsetForVolumeIntent(zoneSamples, intent);
            } else {
                offset = clampGeometricOffset(Math.round(intent / (double) cellCount));
            }
            offset = clampGeometricOffset(offset);
            if (offset != 0) {
                offsets.put(zoneId, offset);
            }
        }
        return Map.copyOf(offsets);
    }

    public static int clampGeometricOffset(long raw) {
        if (raw > MAX_GEOMETRIC_ZONE_OFFSET) {
            return MAX_GEOMETRIC_ZONE_OFFSET;
        }
        if (raw < -MAX_GEOMETRIC_ZONE_OFFSET) {
            return -MAX_GEOMETRIC_ZONE_OFFSET;
        }
        return (int) raw;
    }

    public static BalanceResult propose(
            DesignTerrainGrid grid,
            EarthworkSite site,
            Map<String, com.plot.plugin.earthwork.design.ResolvedDesignSurface> resolvedSurfaces) {
        BalanceResult zoneOnly = proposeZoneOffsetsOnly(grid, site, resolvedSurfaces);
        int residual = 0;
        if (site != null && site.getCompositionPolicy().isBalanceResidualUniformPolish()) {
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
            MaterialConversionModel.DEFAULT);
    }
}
