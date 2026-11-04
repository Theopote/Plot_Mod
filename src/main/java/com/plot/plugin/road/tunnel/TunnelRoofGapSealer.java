package com.plot.plugin.road.tunnel;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.core.terrain.TerrainSampler;

/**
 * Fills vertical air gaps between the tunnel lining roof and original terrain above,
 * and stabilizes a short band of solids so gravity blocks (gravel/sand) cannot cascade
 * into the cavity through a thin or gapped roof shell.
 * <p>
 * Roof top is evaluated <strong>per lateral column</strong> so peaked / circular / elliptical
 * shells are not sealed as a flat rectangle at {@link TunnelCrossSectionMask#maxY()}.
 */
public final class TunnelRoofGapSealer {
    private static final String DEFAULT_TERRAIN_RESTORE = "minecraft:stone";
    /**
     * Replace this many solid layers above the roof with stable stone (covers gravel/sand stacks).
     * Portal faces and steep portals often have thicker gravity stacks than mid-tunnel.
     */
    private static final int STABILIZE_SOLID_LAYERS = 8;

    private TunnelRoofGapSealer() {
    }

    public static void sealAboveRoofShell(
            RoadSolidModel solids,
            TunnelCrossSectionMask mask,
            TunnelShape shape,
            int roadY,
            int clearHeight,
            int roadEnvelopeWidth,
            int sideClearance,
            int liningThickness,
            Vec2d center,
            Vec2d leftNormal,
            double scale,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            TerrainSampler terrain,
            String shellMaterialId,
            String terrainRestoreMaterialId) {
        if (solids == null || mask == null || center == null || leftNormal == null
                || columnResolver == null || terrain == null
                || shellMaterialId == null || shellMaterialId.isBlank()) {
            return;
        }
        String restoreId = terrainRestoreMaterialId != null && !terrainRestoreMaterialId.isBlank()
            ? terrainRestoreMaterialId
            : DEFAULT_TERRAIN_RESTORE;
        int cavityWidth = roadEnvelopeWidth + sideClearance * 2;
        int outerWidth = cavityWidth + liningThickness * 2;
        int outerMin = RoadDimensionUtils.minLateralOffset(outerWidth);
        int outerMax = RoadDimensionUtils.maxLateralOffset(outerWidth);
        int halfCavity = cavityWidth / 2;
        TunnelShape resolved = shape != null ? shape : TunnelShape.ARCH;
        Vec2d normal = leftNormal.lengthSquared() > 1e-12
            ? leftNormal.normalize()
            : new Vec2d(0, 1);

        for (int lateral = outerMin; lateral <= outerMax; lateral++) {
            int roofTop = columnRoofTop(mask, resolved, lateral, halfCavity, roadY, clearHeight, liningThickness);
            if (roofTop == Integer.MIN_VALUE) {
                continue;
            }
            Vec2d point = center.add(normal.multiply(lateral * scale));
            int worldX = columnResolver.worldX(point);
            int worldZ = columnResolver.worldZ(point);
            if (!terrain.isChunkLoaded(worldX, worldZ)) {
                continue;
            }
            int surfaceY = terrain.sampleSurfaceY(point);
            if (roofTop >= surfaceY) {
                continue;
            }
            int columnTop = Math.min(terrain.sampleColumnTopY(point), surfaceY);
            int solidsStabilized = 0;
            for (int y = roofTop + 1; y <= columnTop; y++) {
                if (terrain.isRoadClearableDecoration(worldX, y, worldZ)) {
                    continue;
                }
                if (terrain.isSolidBlock(worldX, y, worldZ)) {
                    solids.add(
                        point,
                        y,
                        RoadSolidLayer.TUNNEL,
                        restoreId,
                        RoadSolidMaterialPriority.STRUCTURAL);
                    solidsStabilized++;
                    if (solidsStabilized >= STABILIZE_SOLID_LAYERS) {
                        break;
                    }
                    continue;
                }
                if (solidsStabilized > 0) {
                    // Reached air after stabilizing solids — stop (do not fill caves above).
                    break;
                }
                solids.add(
                    point,
                    y,
                    RoadSolidLayer.TUNNEL,
                    shellMaterialId,
                    RoadSolidMaterialPriority.STRUCTURAL);
            }
        }
    }

    private static int columnRoofTop(
            TunnelCrossSectionMask mask,
            TunnelShape shape,
            int lateral,
            int halfCavity,
            int roadY,
            int clearHeight,
            int liningThickness) {
        int fromMask = mask.outerTopY(lateral);
        if (fromMask != Integer.MIN_VALUE) {
            return fromMask;
        }
        if (Math.abs(lateral) > halfCavity + liningThickness) {
            return Integer.MIN_VALUE;
        }
        return TunnelProfile.outerCeilingY(
            shape, Math.abs(lateral), halfCavity, roadY, clearHeight, liningThickness);
    }
}
