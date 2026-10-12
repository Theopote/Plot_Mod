package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadConstructionType;
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
        if (solids == null || plan == null || terrain == null) {
            return;
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
        for (ColumnKey key : columns) {
            emitColumn(
                solids,
                plan,
                terrain,
                key,
                fillMaterial,
                bridgeThreshold,
                metrics);
        }
    }

    private static void emitColumn(
            RoadSolidModel solids,
            RoadCorridorPlan plan,
            TerrainSampler terrain,
            ColumnKey key,
            String defaultFillMaterialId,
            int bridgeThreshold,
            RoadEdgeBuildMetrics metrics) {
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

        if (slope != null && slope.hasFill()
                && (constructionType == null || !constructionType.isBridgeFamily())) {
            int fillTop = surfaceY != null
                ? Math.min(slope.fillSurfaceY(), surfaceY - 1)
                : slope.fillSurfaceY();
            if (shouldFillSubgrade(constructionType, fillTop, groundY, bridgeThreshold)) {
                String fillId = slope.fillMaterialId() != null ? slope.fillMaterialId() : defaultFillMaterialId;
                for (int y = groundY + 1; y <= fillTop; y++) {
                    solids.add(planPoint, y, RoadSolidLayer.SHOULDER, fillId);
                }
            }
        } else if (surfaceY != null && groundY < surfaceY - 1 && shouldFillSubgrade(
                constructionType, surfaceY, groundY, bridgeThreshold)) {
            for (int y = groundY + 1; y < surfaceY; y++) {
                solids.add(planPoint, y, RoadSolidLayer.SUBGRADE, defaultFillMaterialId);
                if (metrics != null) {
                    metrics.fillVolume++;
                }
            }
        }

        if (slope != null && slope.hasCut() && groundY > slope.cutSurfaceY()) {
            for (int y = slope.cutSurfaceY() + 1; y <= groundY; y++) {
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
            if (slope.cutMaterialId() != null && !slope.cutMaterialId().isBlank()) {
                solids.add(planPoint, slope.cutSurfaceY(), RoadSolidLayer.SHOULDER, slope.cutMaterialId());
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
        if (constructionType != null && constructionType.isBridgeFamily()) {
            return false;
        }
        if (constructionType == RoadConstructionType.FILL) {
            return true;
        }
        return surfaceY - groundY <= bridgeThreshold;
    }
}
