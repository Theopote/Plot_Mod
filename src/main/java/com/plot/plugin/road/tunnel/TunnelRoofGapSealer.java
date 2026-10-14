package com.plot.plugin.road.tunnel;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.core.terrain.TerrainSampler;

/**
 * Fills vertical air gaps between the tunnel lining roof and original terrain above.
 */
public final class TunnelRoofGapSealer {
    private static final String DEFAULT_TERRAIN_RESTORE = "minecraft:stone";

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
        int roofTop = mask.maxY();
        Vec2d normal = leftNormal.lengthSquared() > 1e-12
            ? leftNormal.normalize()
            : new Vec2d(0, 1);

        for (int lateral = outerMin; lateral <= outerMax; lateral++) {
            if (mask.kindAt(lateral, roofTop) == TunnelCrossSectionMask.CellKind.OUTSIDE) {
                continue;
            }
            Vec2d point = center.add(normal.multiply(lateral * scale));
            int worldX = columnResolver.worldX(point);
            int worldZ = columnResolver.worldZ(point);
            if (!terrain.isChunkLoaded(worldX, worldZ)) {
                continue;
            }
            int columnTop = terrain.sampleColumnTopY(point);
            for (int y = roofTop + 1; y <= columnTop; y++) {
                if (terrain.isSolidBlock(worldX, y, worldZ)) {
                    solids.add(
                        point,
                        y,
                        RoadSolidLayer.TUNNEL,
                        restoreId,
                        RoadSolidMaterialPriority.STRUCTURAL);
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
}
