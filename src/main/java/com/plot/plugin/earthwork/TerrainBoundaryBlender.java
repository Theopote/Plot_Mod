package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;

import java.util.Map;

/**
 * 分区交界高程混合（{@link CompositionPolicy#getBlendWidthBlocks()}）。
 */
public final class TerrainBoundaryBlender {

    private TerrainBoundaryBlender() {
    }

    public static void apply(
            DesignTerrainGrid grid,
            EarthworkSite site,
            Map<Long, ZoneCoverage> coverageByCellKey) {
        if (grid == null || site == null || coverageByCellKey == null || coverageByCellKey.isEmpty()) {
            return;
        }
        int blendWidth = site.getCompositionPolicy().getBlendWidthBlocks();
        if (blendWidth <= 0) {
            return;
        }

        for (DesignTerrainCell cell : grid.cells().values()) {
            if (cell == null || cell.excluded() || !cell.participatesInEarthwork()) {
                continue;
            }
            ZoneCoverage coverage = coverageByCellKey.get(
                DesignTerrainGrid.cellKey(cell.worldX(), cell.worldZ()));
            if (coverage == null || coverage.runnerUpTargetY() == null) {
                continue;
            }
            GradingZone winnerZone = site.getZone(cell.zoneId());
            if (winnerZone == null) {
                continue;
            }
            double distanceToEdge = EarthworkGeometryUtils.distanceToPolygonBoundary(
                winnerZone.getOuterPoints(),
                cell.center());
            if (distanceToEdge >= blendWidth) {
                continue;
            }
            double factor = Math.min(1.0, distanceToEdge / blendWidth);
            int winnerTarget = cell.targetY();
            int neighborTarget = coverage.runnerUpTargetY();
            int blended = (int) Math.round(neighborTarget * (1.0 - factor) + winnerTarget * factor);
            cell.setTargetY(blended);
        }
    }

    public record ZoneCoverage(int winnerTargetY, Integer runnerUpTargetY) {
    }
}
