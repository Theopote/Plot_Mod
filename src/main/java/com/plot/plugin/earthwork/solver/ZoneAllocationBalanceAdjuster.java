package com.plot.plugin.earthwork.solver;

import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;

import java.util.List;
import java.util.Map;

/**
 * @deprecated 命名混淆了「土方调配报告」与「竖向优化」。请使用
 * {@link EarthworkOptimizationSolver}（Mode B）与 {@link EarthworkAllocationMatrix}（Mode A）。
 * 本类保留为委托门面，兼容旧测试与调用。
 */
@Deprecated
public final class ZoneAllocationBalanceAdjuster {

    private ZoneAllocationBalanceAdjuster() {
    }

    /**
     * @deprecated 使用 {@link EarthworkOptimizationSolver.BalanceResult}
     */
    @Deprecated
    public record BalanceResult(
            Map<String, Integer> zoneOffsets,
            int residualUniformOffset) {
        public boolean isZero() {
            return toSolverResult().isZero();
        }

        private EarthworkOptimizationSolver.BalanceResult toSolverResult() {
            return new EarthworkOptimizationSolver.BalanceResult(zoneOffsets, residualUniformOffset);
        }

        static BalanceResult from(EarthworkOptimizationSolver.BalanceResult result) {
            if (result == null) {
                return new BalanceResult(Map.of(), 0);
            }
            return new BalanceResult(result.zoneOffsets(), result.residualUniformOffset());
        }
    }

    public static BalanceResult propose(DesignTerrainGrid grid, EarthworkSite site) {
        return BalanceResult.from(EarthworkOptimizationSolver.propose(grid, site));
    }

    public static BalanceResult balance(DesignTerrainGrid grid, EarthworkSite site) {
        return BalanceResult.from(EarthworkOptimizationSolver.balance(grid, site));
    }

    public static Map<String, Integer> computeZoneOffsets(
            EarthworkAllocationMatrix matrix,
            Map<String, Integer> cellCountByZone) {
        return EarthworkOptimizationSolver.computeZoneOffsets(matrix, cellCountByZone);
    }

    public static Map<String, Integer> computeZoneOffsets(
            EarthworkAllocationMatrix matrix,
            Map<String, Integer> cellCountByZone,
            Map<String, List<SiteWideBalanceAdjuster.CellSample>> samplesByZone,
            MaterialConversionModel materials) {
        return EarthworkOptimizationSolver.computeZoneOffsets(
            matrix, cellCountByZone, samplesByZone, materials);
    }

    public static Map<String, Integer> computeZoneOffsets(
            EarthworkAllocationMatrix matrix,
            Map<String, Integer> cellCountByZone,
            Map<String, List<SiteWideBalanceAdjuster.CellSample>> samplesByZone,
            EarthworkSite site,
            Map<String, EarthworkVolumeReport> byZone) {
        return EarthworkOptimizationSolver.computeZoneOffsets(
            matrix, cellCountByZone, samplesByZone, site, byZone);
    }

    public static Map<String, Integer> computeZoneOffsets(
            EarthworkAllocationMatrix matrix,
            Map<String, Integer> cellCountByZone,
            Map<String, List<SiteWideBalanceAdjuster.CellSample>> samplesByZone,
            EarthworkSite site,
            Map<String, EarthworkVolumeReport> byZone,
            MaterialConversionModel fallbackMaterials) {
        return EarthworkOptimizationSolver.computeZoneOffsets(
            matrix, cellCountByZone, samplesByZone, site, byZone, fallbackMaterials);
    }

    public static Map<String, List<SiteWideBalanceAdjuster.CellSample>> collectSamplesByZone(
            DesignTerrainGrid grid) {
        return EarthworkOptimizationSolver.collectSamplesByZone(grid);
    }

    public static void applyZoneOffsets(DesignTerrainGrid grid, Map<String, Integer> zoneOffsets) {
        EarthworkOptimizationSolver.applyZoneOffsets(grid, zoneOffsets);
    }

    public static SiteEarthworkReport collectZoneVolumes(DesignTerrainGrid grid) {
        return EarthworkOptimizationSolver.collectZoneVolumes(grid);
    }

    public static SiteEarthworkReport collectZoneVolumes(DesignTerrainGrid grid, EarthworkSite site) {
        return EarthworkOptimizationSolver.collectZoneVolumes(grid, site);
    }

    public static Map<String, Integer> countCellsByZone(DesignTerrainGrid grid) {
        return EarthworkOptimizationSolver.countCellsByZone(grid);
    }
}
