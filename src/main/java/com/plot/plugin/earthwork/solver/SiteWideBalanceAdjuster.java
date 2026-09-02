package com.plot.plugin.earthwork.solver;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.EarthMaterialProperties;

import java.util.ArrayList;
import java.util.List;

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
            EarthMaterialProperties materials) {
        if (samples == null || samples.isEmpty()) {
            return 0;
        }
        List<Integer> residuals = new ArrayList<>(samples.size());
        for (CellSample sample : samples) {
            residuals.add(sample.existingGroundY() - sample.rawDesignTargetY());
        }
        return EarthworkBalanceUtils.findBalancedElevation(residuals, materials);
    }

    public static void applyOffset(DesignTerrainGrid grid, int verticalOffset) {
        if (grid == null || verticalOffset == 0) {
            return;
        }
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (!cell.participatesInEarthwork() || cell.zoneId() == null) {
                continue;
            }
            cell.setTargetY(cell.targetY() + verticalOffset);
        }
    }
}
