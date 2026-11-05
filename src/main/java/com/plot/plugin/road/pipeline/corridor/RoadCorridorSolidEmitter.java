package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.construction.RoadEarthworkPolicy;
import com.plot.plugin.road.pipeline.RoadEdgeBuildMetrics;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.core.terrain.TerrainSampler;

import java.util.HashSet;
import java.util.Set;

/**
 * 从 {@link RoadCorridorPlan} 写入路面、边坡与路基填方体素。
 */
public final class RoadCorridorSolidEmitter {
    private RoadCorridorSolidEmitter() {
    }

    public static void emit(
            RoadSolidModel solids,
            RoadCorridorPlan plan,
            TerrainSampler terrain,
            RoadCorridorPlanBuilder.PlanHost host,
            RoadEdgeBuildMetrics metrics,
            String defaultFillMaterialId) {
        emit(solids, plan, terrain, host, metrics, defaultFillMaterialId, GradeSeparationBuildContext.NONE);
    }

    public static void emit(
            RoadSolidModel solids,
            RoadCorridorPlan plan,
            TerrainSampler terrain,
            RoadCorridorPlanBuilder.PlanHost host,
            RoadEdgeBuildMetrics metrics,
            String defaultFillMaterialId,
            GradeSeparationBuildContext gradeSeparation) {
        if (solids == null || plan == null || terrain == null) {
            return;
        }
        if (gradeSeparation == null) {
            gradeSeparation = GradeSeparationBuildContext.NONE;
        }
        String fillMaterial = defaultFillMaterialId != null && !defaultFillMaterialId.isBlank()
            ? defaultFillMaterialId
            : host.resolveBlockId(host.config().getFillSlopeMaterial());

        Set<ColumnKey> columns = new HashSet<>();
        for (var entry : plan.surfaceMask().entries()) {
            columns.add(entry.getKey());
        }
        for (var entry : plan.slopeEntries()) {
            columns.add(entry.getKey());
        }

        int bridgeThreshold = host.bridgeThreshold();
        PortalTransitionGuard portalGuard = PortalTransitionGuard.from(plan);
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(host.config());
        TunnelMountainProtection mountainProtection = TunnelMountainProtection.from(plan, crossSection);
        int envelopeWidth = RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection);
        int roadHalfWidth = Math.abs(RoadDimensionUtils.maxLateralOffset(envelopeWidth));
        for (ColumnKey key : columns) {
            emitColumn(
                solids,
                plan,
                terrain,
                key,
                fillMaterial,
                bridgeThreshold,
                metrics,
                portalGuard,
                mountainProtection,
                roadHalfWidth,
                host,
                gradeSeparation);
        }
    }

    private static void emitColumn(
            RoadSolidModel solids,
            RoadCorridorPlan plan,
            TerrainSampler terrain,
            ColumnKey key,
            String defaultFillMaterialId,
            int bridgeThreshold,
            RoadEdgeBuildMetrics metrics,
            PortalTransitionGuard portalGuard,
            TunnelMountainProtection mountainProtection,
            int roadHalfWidth,
            RoadCorridorPlanBuilder.PlanHost host,
            GradeSeparationBuildContext gradeSeparation) {
        if (!terrain.isChunkLoaded(key.worldX(), key.worldZ())) {
            return;
        }
        SurfaceCell surface = plan.surfaceMask().get(key);
        SlopeColumnCell slope = plan.slopeCell(key);
        ClearanceCell clearance = plan.clearanceCell(key);
        Vec2d planPoint = surface != null
            ? surface.planPoint()
            : slope != null ? slope.planPoint() : new Vec2d(key.worldX(), key.worldZ());
        int groundY = terrain.sampleSurfaceY(planPoint);
        Integer surfaceY = surface != null ? surface.surfaceY() : null;
        RoadConstructionType constructionType = clearance != null ? clearance.constructionType() : null;
        boolean bridgeClearance = clearance != null && clearance.mode() == RoadCorridorConstructionMode.BRIDGE;
        if (plan.isSlopeColumnIncomplete(key)) {
            if (surface != null) {
                solids.add(planPoint, surface.surfaceY(), surface.layer(), surface.materialId());
            }
            return;
        }

        // 立交上跨边：禁止开敞填方边坡/路基堆土（会掉进下层净空或成悬浮土堤）
        boolean elevatedEdge = gradeSeparation != null && gradeSeparation.elevatedRoadEdge();
        var waterSurface = terrain.findExposedWaterSurface(planPoint);
        boolean waterBlocksSlopeFill = RoadEarthworkPolicy.suppressFillOverWater(
            true, plan.allowsSlopeWaterFill(key), waterSurface);
        boolean waterBlocksRoadbedFill = RoadEarthworkPolicy.suppressFillOverWater(
            true, plan.allowsRoadbedWaterFill(key), waterSurface);
        boolean allowOpenFillBase = !elevatedEdge
            && !bridgeClearance
            && (constructionType == null
                || RoadEarthworkPolicy.of(constructionType).allowsOpenSubgradeFill());
        if (allowOpenFillBase && !waterBlocksSlopeFill && slope != null && slope.hasFill()) {
            int fillTop = surfaceY != null
                ? Math.min(slope.fillSurfaceY(), surfaceY - 1)
                : slope.fillSurfaceY();
            fillTop = portalGuard.capSlopeSurfaceY(planPoint, fillTop, host, roadHalfWidth);
            if (shouldFillSubgrade(constructionType, fillTop, groundY, bridgeThreshold)) {
                String fillId = slope.fillMaterialId() != null ? slope.fillMaterialId() : defaultFillMaterialId;
                for (int y = groundY + 1; y <= fillTop; y++) {
                    if (gradeSeparation != null && gradeSeparation.protects(planPoint, y)) {
                        continue;
                    }
                    solids.add(planPoint, y, RoadSolidLayer.SUBGRADE, fillId);
                    if (metrics != null) {
                        metrics.fillVolume++;
                    }
                }
            }
        } else if (allowOpenFillBase && !waterBlocksRoadbedFill && surfaceY != null
                && groundY < surfaceY - 1
                && shouldFillSubgrade(constructionType, surfaceY, groundY, bridgeThreshold)) {
            for (int y = groundY + 1; y < surfaceY; y++) {
                if (gradeSeparation != null && gradeSeparation.protects(planPoint, y)) {
                    continue;
                }
                solids.add(planPoint, y, RoadSolidLayer.SUBGRADE, defaultFillMaterialId);
                if (metrics != null) {
                    metrics.fillVolume++;
                }
            }
        }

        // 隧道族净空柱、洞顶保护包络、深覆土柱禁止开放切方（洞穴/峡谷误判 CUT 时也不削山）
        boolean tunnelFamilyColumn = constructionType != null
            && (constructionType.isTunnelFamily() || constructionType.isRockShelter());
        boolean mountainProtected = mountainProtection != null && mountainProtection.protects(key);
        // 仅填方边坡时 cutSurfaceY 为 null，不可拆箱
        int designY = resolveDesignY(surfaceY, slope, groundY);
        boolean deepCoverColumn = RoadEarthworkPolicy.suppressOpenDaylightBatter(
            constructionType,
            false,
            groundY - designY,
            host.tunnelStyle(),
            host.tunnelThreshold());
        // SlopeEnvelopeClearancePass 已为切坡列建清障时，挖方量只在 ClearancePass 计一次。
        boolean cutCountedInClearance = clearance != null
            && clearance.mode() == RoadCorridorConstructionMode.OPEN
            && slope != null
            && slope.hasCut();
        if (slope != null && slope.hasCut() && groundY > slope.cutSurfaceY()
                && !tunnelFamilyColumn
                && !mountainProtected
                && !deepCoverColumn
                && (constructionType == null
                    || RoadEarthworkPolicy.allowsCorridorBatter(constructionType))) {
            int cutTopY = portalGuard.capCutTopY(planPoint, groundY, host, roadHalfWidth);
            int cutFaceY = portalGuard.capSlopeSurfaceY(planPoint, slope.cutSurfaceY(), host, roadHalfWidth);
            if (cutFaceY >= cutTopY) {
                cutFaceY = cutTopY - 1;
            }
            boolean retainingTip = slope.needsRetainingFinish();
            for (int y = cutFaceY + 1; y <= cutTopY; y++) {
                if (gradeSeparation != null && gradeSeparation.protectsClearance(planPoint, y)) {
                    continue;
                }
                // 挡墙将占据 faceY..groundY-1；同柱清障 AIR 不得抢占。
                if (retainingTip && y >= cutFaceY && y < groundY) {
                    continue;
                }
                solids.add(
                    planPoint,
                    y,
                    RoadSolidLayer.TUNNEL,
                    "minecraft:air",
                    RoadSolidMaterialPriority.AIR,
                    ClearancePurpose.SLOPE_EXCAVATION);
                if (metrics != null && !cutCountedInClearance) {
                    metrics.cutVolume++;
                }
            }
            if ((surface == null || cutFaceY < surface.surfaceY()) && cutFaceY < cutTopY) {
                String batterId = slope.cutMaterialId() != null && !slope.cutMaterialId().isBlank()
                    ? slope.cutMaterialId()
                    : defaultFillMaterialId;
                if (batterId != null && !batterId.isBlank()) {
                    solids.add(planPoint, cutFaceY, RoadSolidLayer.SUBGRADE, batterId);
                }
            }
        }

        if (slope != null && slope.needsRetainingFinish() && !waterBlocksSlopeFill) {
            emitRetainingFinish(
                solids, slope, planPoint, groundY, defaultFillMaterialId, gradeSeparation);
        }

        if (surface != null) {
            solids.add(planPoint, surface.surfaceY(), surface.layer(), surface.materialId());
        }
    }

    /**
     * 宽度上限截断收尾：在最外坡柱用竖向支撑接到自然地面，避免悬空半坡。
     */
    private static void emitRetainingFinish(
            RoadSolidModel solids,
            SlopeColumnCell slope,
            Vec2d planPoint,
            int groundY,
            String defaultFillMaterialId,
            GradeSeparationBuildContext gradeSeparation) {
        if (solids == null || slope == null || planPoint == null) {
            return;
        }
        if (slope.hasCut()) {
            int faceY = slope.cutSurfaceY();
            String material = slope.cutMaterialId() != null && !slope.cutMaterialId().isBlank()
                ? slope.cutMaterialId()
                : defaultFillMaterialId;
            if (material == null || material.isBlank() || groundY <= faceY) {
                return;
            }
            for (int y = faceY; y < groundY; y++) {
                if (gradeSeparation != null && gradeSeparation.protects(planPoint, y)) {
                    continue;
                }
                solids.add(planPoint, y, RoadSolidLayer.RETAINING_WALL, material);
            }
            return;
        }
        if (slope.hasFill()) {
            int faceY = slope.fillSurfaceY();
            String material = slope.fillMaterialId() != null && !slope.fillMaterialId().isBlank()
                ? slope.fillMaterialId()
                : defaultFillMaterialId;
            if (material == null || material.isBlank() || faceY <= groundY) {
                return;
            }
            for (int y = groundY + 1; y <= faceY; y++) {
                if (gradeSeparation != null && gradeSeparation.protects(planPoint, y)) {
                    continue;
                }
                solids.add(planPoint, y, RoadSolidLayer.RETAINING_WALL, material);
            }
        }
    }

    private static boolean shouldFillSubgrade(
            RoadConstructionType constructionType,
            int surfaceY,
            int groundY,
            int bridgeThreshold) {
        if (constructionType != null
                && !RoadEarthworkPolicy.of(constructionType).allowsOpenSubgradeFill()) {
            return false;
        }
        return RoadEarthworkPolicy.allowsOpenFillHeight(surfaceY, groundY, bridgeThreshold);
    }

    /** 覆土判定用的设计标高：路面优先，其次切/填坡面，避免仅填方柱上 cutSurfaceY 空拆箱。 */
    static int resolveDesignY(Integer surfaceY, SlopeColumnCell slope, int groundY) {
        if (surfaceY != null) {
            return surfaceY;
        }
        if (slope != null && slope.hasCut()) {
            return slope.cutSurfaceY();
        }
        if (slope != null && slope.hasFill()) {
            return slope.fillSurfaceY();
        }
        return groundY;
    }
}
