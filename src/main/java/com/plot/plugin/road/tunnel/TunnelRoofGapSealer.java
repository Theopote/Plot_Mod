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
 * <p>
 * 小孔洞（≤ {@link TunnelOverburdenPolicy#SMALL_AIR_POCKET_MAX_BLOCKS}）按正常地层封填；
 * 更大空腔视为洞穴，停止向上填充，避免整穴砌死。
 */
public final class TunnelRoofGapSealer {
    private static final String DEFAULT_TERRAIN_RESTORE = "minecraft:stone";
    /**
     * 单柱重力覆土改写上限。沙丘可远厚于旧值 8；封顶须接到非重力承重或自然地表，
     * 否则洞顶只稳一小段，上方沙子仍会在壳缝处塌入洞腔。
     */
    private static final int STABILIZE_GRAVITY_LAYERS = 64;
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
        for (int y = roofTop + 1; y <= columnTop; ) {
            if (terrain.isRoadClearableDecoration(worldX, y, worldZ)) {
                y++;
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
                y++;
                continue;
            }
            if (terrain.isSolidBlock(worldX, y, worldZ)) {
                // 稳定承重层：支撑链到此结束。不得 continue 扫到更高处无关沙砾再改写。
                break;
            }
            if (reachedOverburden) {
                // 已接到覆土后再遇空洞：大洞穴不填；小孔仍可补实后继续稳定化。
                int voidRun = TunnelOverburdenPolicy.measureSealableVoidRun(
                    terrain, worldX, y, worldZ, columnTop);
                if (!TunnelOverburdenPolicy.isSmallSealableVoidRun(voidRun)) {
                    break;
                }
                fillVoidRun(solids, point, y, voidRun, restoreId);
                y += voidRun;
                continue;
            }
            int voidRun = TunnelOverburdenPolicy.measureSealableVoidRun(
                terrain, worldX, y, worldZ, columnTop);
            if (!TunnelOverburdenPolicy.isSmallSealableVoidRun(voidRun)) {
                // 洞顶上方大空腔：不整穴用衬砌填死。
                break;
            }
            // 小孔洞（含积水/岩浆格）当不存在：补实后按正常开挖继续。
            String fillId = shellMaterialId != null && !shellMaterialId.isBlank()
                ? shellMaterialId
                : restoreId;
            fillVoidRun(solids, point, y, voidRun, fillId);
            y += voidRun;
        }
    }

    private static void fillVoidRun(
            RoadSolidModel solids,
            Vec2d point,
            int fromY,
            int voidRun,
            String fillId) {
        if (solids == null || point == null || fillId == null || fillId.isBlank() || voidRun <= 0) {
            return;
        }
        for (int i = 0; i < voidRun; i++) {
            solids.add(
                point,
                fromY + i,
                RoadSolidLayer.TUNNEL,
                fillId,
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
