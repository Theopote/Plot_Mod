package com.plot.plugin.road.tunnel;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.core.terrain.TerrainSampler;

/**
 * Validates that a tunnel cavity can be fully covered by solid terrain above the structure envelope.
 */
public final class TunnelFeasibilityChecker {
    private TunnelFeasibilityChecker() {
    }

    public static TunnelFeasibility check(
            TerrainSampler terrain,
            Vec2d center,
            Vec2d leftNormal,
            int roadY,
            int envelopeWidth,
            ResolvedTunnelStyle style,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double unitsPerBlock) {
        if (terrain == null || center == null || leftNormal == null || style == null
                || columnResolver == null || envelopeWidth <= 0) {
            return TunnelFeasibility.invalid();
        }
        int cavityWidth = envelopeWidth + style.sideClearance() * 2;
        int outerWidth = cavityWidth + style.liningThickness() * 2;
        int outerMin = RoadDimensionUtils.minLateralOffset(outerWidth);
        int outerMax = RoadDimensionUtils.maxLateralOffset(outerWidth);
        int roofY = roadY + style.roofTopOffset();
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        Vec2d normal = leftNormal.lengthSquared() > 1e-12
            ? leftNormal.normalize()
            : new Vec2d(0, 1);

        int centerX = columnResolver.worldX(center);
        int centerZ = columnResolver.worldZ(center);
        if (!terrain.isSolidBlock(centerX, roadY, centerZ)) {
            return TunnelFeasibility.invalid();
        }

        int minCover = Integer.MAX_VALUE;
        int checkedColumns = 0;
        int coveredColumns = 0;
        for (int lateral = outerMin; lateral <= outerMax; lateral++) {
            Vec2d point = center.add(normal.multiply(lateral * scale));
            int worldX = columnResolver.worldX(point);
            int worldZ = columnResolver.worldZ(point);
            checkedColumns++;
            int cover = measureCoverAbove(terrain, worldX, worldZ, roofY);
            minCover = Math.min(minCover, cover);
            if (cover >= TunnelFeasibility.MINIMUM_COVER_BLOCKS) {
                coveredColumns++;
            }
        }
        if (checkedColumns == 0) {
            return TunnelFeasibility.invalid();
        }
        double ratio = (double) coveredColumns / checkedColumns;
        boolean valid = minCover >= TunnelFeasibility.MINIMUM_COVER_BLOCKS && ratio >= 0.75;
        return new TunnelFeasibility(valid, minCover == Integer.MAX_VALUE ? 0 : minCover, ratio);
    }

    private static int measureCoverAbove(TerrainSampler terrain, int worldX, int worldZ, int roofY) {
        int cover = 0;
        for (int y = roofY + 1; y <= roofY + 12; y++) {
            if (terrain.isSolidBlock(worldX, y, worldZ)) {
                cover++;
            } else if (cover > 0) {
                break;
            }
        }
        return cover;
    }
}
