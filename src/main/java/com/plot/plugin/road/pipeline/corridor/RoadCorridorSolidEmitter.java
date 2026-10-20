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
        int envelopeWidth = RoadCorridorWidth.gradingEnvelopeWidthBlocks(
            ResolvedCrossSection.fromConfig(host.config()));
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

        if (slope != null && slope.hasFill()
                && (constructionType == null
                    || RoadEarthworkPolicy.of(constructionType).allowsOpenSubgradeFill())) {
            int fillTop = surfaceY != null
                ? Math.min(slope.fillSurfaceY(), surfaceY - 1)
                : slope.fillSurfaceY();
            fillTop = portalGuard.capSlopeSurfaceY(planPoint, fillTop, host, roadHalfWidth);
            if (shouldFillSubgrade(constructionType, fillTop, groundY, bridgeThreshold)) {
                String fillId = slope.fillMaterialId() != null ? slope.fillMaterialId() : defaultFillMaterialId;
                for (int y = groundY + 1; y <= fillTop; y++) {
                    if (gradeSeparation.protects(planPoint, y)) {
                        continue;
                    }
                    solids.add(planPoint, y, RoadSolidLayer.SUBGRADE, fillId);
                }
            }
        } else if (!bridgeClearance && surfaceY != null && groundY < surfaceY - 1 && shouldFillSubgrade(
                constructionType, surfaceY, groundY, bridgeThreshold)) {
            for (int y = groundY + 1; y < surfaceY; y++) {
                if (gradeSeparation.protects(planPoint, y)) {
                    continue;
                }
                solids.add(planPoint, y, RoadSolidLayer.SUBGRADE, defaultFillMaterialId);
                if (metrics != null) {
                    metrics.fillVolume++;
                }
            }
        }

        if (slope != null && slope.hasCut() && groundY > slope.cutSurfaceY()
                && (constructionType == null
                    || RoadEarthworkPolicy.of(constructionType).allowsCorridorBatter())) {
            int cutTopY = portalGuard.capCutTopY(planPoint, groundY, host, roadHalfWidth);
            int cutFaceY = portalGuard.capSlopeSurfaceY(planPoint, slope.cutSurfaceY(), host, roadHalfWidth);
            if (cutFaceY >= cutTopY) {
                cutFaceY = cutTopY - 1;
            }
            for (int y = cutFaceY + 1; y <= cutTopY; y++) {
                if (gradeSeparation.protectsClearance(planPoint, y)) {
                    continue;
                }
                solids.add(
                    planPoint,
                    y,
                    RoadSolidLayer.TUNNEL,
                    "minecraft:air",
                    RoadSolidMaterialPriority.AIR);
                if (metrics != null) {
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

        if (surface != null) {
            solids.add(planPoint, surface.surfaceY(), surface.layer(), surface.materialId());
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
        if (constructionType == RoadConstructionType.FILL) {
            return true;
        }
        return surfaceY - groundY <= bridgeThreshold;
    }
}
