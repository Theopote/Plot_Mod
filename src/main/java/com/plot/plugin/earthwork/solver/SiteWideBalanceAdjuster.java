package com.plot.plugin.earthwork.solver;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.core.material.MaterialConversionModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 全场土方平衡：对合成后的设计面施加统一竖向偏移 ΔY，使挖填趋于平衡。
 */
public final class SiteWideBalanceAdjuster {

    public record CellSample(int existingGroundY, int rawDesignTargetY) {
    }

    private SiteWideBalanceAdjuster() {
    }

    /**
     * 求使全场材料挖填差最小的竖向偏移量。
     * <p>
     * 对每格令调整后目标 = rawDesignTargetY + ΔY，等价于对残差
     * {@code existingGroundY - rawDesignTargetY} 做一维平衡求解。
     */
    public static int findBalancedVerticalOffset(
            List<CellSample> samples,
            MaterialConversionModel materials) {
        return findBalancedVerticalOffset(samples, List.of(), materials);
    }

    /**
     * 仅对可变格点搜索 ΔY；锁定格点的挖填作为固定材料残差。
     */
    public static int findBalancedVerticalOffset(
            List<CellSample> flexibleSamples,
            List<CellSample> lockedSamples,
            MaterialConversionModel materials) {
        if (flexibleSamples == null || flexibleSamples.isEmpty()) {
            return 0;
        }
        List<Integer> residuals = new ArrayList<>(flexibleSamples.size());
        for (CellSample sample : flexibleSamples) {
            residuals.add(sample.existingGroundY() - sample.rawDesignTargetY());
        }
        long lockedDiff = 0L;
        if (lockedSamples != null && !lockedSamples.isEmpty()) {
            List<Integer> lockedResiduals = new ArrayList<>(lockedSamples.size());
            for (CellSample sample : lockedSamples) {
                lockedResiduals.add(sample.existingGroundY() - sample.rawDesignTargetY());
            }
            lockedDiff = EarthworkBalanceUtils.computeBalanceDiff(lockedResiduals, 0, materials);
        }
        return EarthworkBalanceUtils.findBalancedElevation(residuals, lockedDiff, materials);
    }

    public static int findBalancedVerticalOffset(DesignTerrainGrid grid, EarthworkSite site) {
        if (grid == null) {
            return 0;
        }
        List<CellSample> flexible = new ArrayList<>();
        List<CellSample> locked = new ArrayList<>();
        splitSamples(grid, site, Map.of(), flexible, locked);
        MaterialConversionModel materials = site != null ? site.getMaterialModel() : MaterialConversionModel.DEFAULT;
        return findBalancedVerticalOffset(flexible, locked, materials);
    }

    public static void splitSamples(
            DesignTerrainGrid grid,
            EarthworkSite site,
            Map<String, Integer> extraZoneOffsets,
            List<CellSample> flexibleOut,
            List<CellSample> lockedOut) {
        Map<String, Integer> safeOffsets = extraZoneOffsets != null ? extraZoneOffsets : Map.of();
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (!cell.participatesInEarthwork() || cell.zoneId() == null || cell.zoneId().isBlank()) {
                continue;
            }
            int targetY = cell.targetY() + safeOffsets.getOrDefault(cell.zoneId(), 0);
            CellSample sample = new CellSample(cell.existingGroundY(), targetY);
            if (site != null && site.isElevationLocked(cell.zoneId())) {
                lockedOut.add(new CellSample(cell.existingGroundY(), cell.targetY()));
            } else {
                flexibleOut.add(sample);
            }
        }
    }

    public static void applyOffset(DesignTerrainGrid grid, int verticalOffset) {
        applyOffset(grid, verticalOffset, null);
    }

    public static void applyOffset(DesignTerrainGrid grid, int verticalOffset, EarthworkSite site) {
        if (grid == null || verticalOffset == 0) {
            return;
        }
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (!cell.participatesInEarthwork() || cell.zoneId() == null) {
                continue;
            }
            if (site != null && site.isElevationLocked(cell.zoneId())) {
                continue;
            }
            cell.setTargetY(cell.targetY() + verticalOffset);
        }
    }
}
