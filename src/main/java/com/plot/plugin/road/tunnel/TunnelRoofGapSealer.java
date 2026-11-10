package com.plot.plugin.road.tunnel;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.core.terrain.TerrainSampler;

import java.util.HashSet;
import java.util.Set;

/**
 * Fills vertical air gaps between the tunnel lining roof and original terrain above,
 * and stabilizes gravity-affected stacks so sand/gravel cannot cascade into the cavity.
 * <p>
 * Roof top is evaluated <strong>per lateral column</strong> so peaked / circular / elliptical
 * shells are not sealed as a flat rectangle at {@link TunnelCrossSectionMask#maxY()}.
 * Stable engineering solids (dirt, stone, …) are left in place — only gravity blocks are rewritten.
 */
public final class TunnelRoofGapSealer {
    private static final String DEFAULT_TERRAIN_RESTORE = "minecraft:stone";
    /**
     * Cap on gravity-block replacements per column (thick portal gravel stacks).
     * Does not rewrite ordinary solid terrain.
     */
    private static final int STABILIZE_GRAVITY_LAYERS = 8;
    /** Match lining/cavity insets so diagonal world columns are not missed. */
    private static final double[] CELL_INSETS = {0.0, -0.25, 0.25};

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
        double resolvedScale = scale > 1e-9 ? scale : 1.0;
        Set<Long> sealedColumns = new HashSet<>();

        for (int lateral = outerMin; lateral <= outerMax; lateral++) {
            int roofTop = columnRoofTop(mask, resolved, lateral, halfCavity, roadY, clearHeight, liningThickness);
            if (roofTop == Integer.MIN_VALUE) {
                continue;
            }
            for (double inset : CELL_INSETS) {
                Vec2d point = center.add(normal.multiply((lateral + inset) * resolvedScale));
                int worldX = columnResolver.worldX(point);
                int worldZ = columnResolver.worldZ(point);
                long columnKey = (((long) worldX) << 32) ^ (worldZ & 0xffffffffL);
                if (!sealedColumns.add(columnKey)) {
                    continue;
                }
                sealColumn(
                    solids, point, worldX, worldZ, roofTop, terrain, shellMaterialId, restoreId);
            }
        }
    }

    /**
     * Stabilize gravity blocks above a single world column whose cavity was cleared
     * without a full cross-section stamp (corridor tunnel clearance / acute gore).
     */
    public static void stabilizeGravityColumn(
            RoadSolidModel solids,
            Vec2d planPoint,
            int worldX,
            int worldZ,
            int roofTop,
            TerrainSampler terrain,
            String terrainRestoreMaterialId) {
        if (solids == null || planPoint == null || terrain == null || roofTop == Integer.MIN_VALUE) {
            return;
        }
        String restoreId = terrainRestoreMaterialId != null && !terrainRestoreMaterialId.isBlank()
            ? terrainRestoreMaterialId
            : DEFAULT_TERRAIN_RESTORE;
        // Gravity-only: do not invent shell fill for columns without a designed roof stamp.
        sealColumn(solids, planPoint, worldX, worldZ, roofTop, terrain, null, restoreId);
    }

    private static void sealColumn(
            RoadSolidModel solids,
            Vec2d point,
            int worldX,
            int worldZ,
            int roofTop,
            TerrainSampler terrain,
            String shellMaterialId,
            String restoreId) {
        if (!terrain.isChunkLoaded(worldX, worldZ)) {
            return;
        }
        int surfaceY = terrain.sampleSurfaceY(point);
        // 硬上限：自然工程地表及其上方不得补衬砌/补强（浅覆土洞口禁止凭空抬到 roofTop+1）。
        int maxAuthorizedY = surfaceY - 1;
        if (maxAuthorizedY < roofTop) {
            return;
        }
        // 仅当设计顶衬格仍在地表以下时，才把该格重力块改写为承重。
        if (roofTop < surfaceY && terrain.isGravityAffectedBlock(worldX, roofTop, worldZ)) {
            String roofFill = shellMaterialId != null && !shellMaterialId.isBlank()
                ? shellMaterialId
                : restoreId;
            solids.add(
                point,
                roofTop,
                RoadSolidLayer.TUNNEL,
                roofFill,
                RoadSolidMaterialPriority.STRUCTURAL);
        }
        int columnTop = Math.max(terrain.sampleColumnTopY(point), surfaceY);
        columnTop = Math.min(columnTop, maxAuthorizedY);
        if (columnTop <= roofTop) {
            return;
        }
        int gravityStabilized = 0;
        boolean reachedOverburden = false;
        for (int y = roofTop + 1; y <= columnTop; y++) {
            if (terrain.isRoadClearableDecoration(worldX, y, worldZ)) {
                continue;
            }
            if (terrain.isGravityAffectedBlock(worldX, y, worldZ)) {
                solids.add(
                    point,
                    y,
                    RoadSolidLayer.TUNNEL,
                    restoreId,
                    RoadSolidMaterialPriority.STRUCTURAL);
                gravityStabilized++;
                reachedOverburden = true;
                if (gravityStabilized >= STABILIZE_GRAVITY_LAYERS) {
                    break;
                }
                continue;
            }
            if (terrain.isSolidBlock(worldX, y, worldZ)) {
                // 稳定承重层：支撑链到此结束。不得 continue 扫到更高处无关沙砾再改写。
                break;
            }
            if (reachedOverburden) {
                // Air after overburden — do not fill caves above the roof stack.
                break;
            }
            if (shellMaterialId == null || shellMaterialId.isBlank()) {
                // Gravity-only column stabilize: leave air gaps; only rewrite falling blocks.
                continue;
            }
            solids.add(
                point,
                y,
                RoadSolidLayer.TUNNEL,
                shellMaterialId,
                RoadSolidMaterialPriority.STRUCTURAL);
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
