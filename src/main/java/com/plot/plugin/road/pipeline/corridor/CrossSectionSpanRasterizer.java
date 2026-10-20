package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.solid.RoadVoxelRasterizer;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;

/**
 * Stamps a full cross-section at each centerline sample so adjacent bands share coverage.
 */
public final class CrossSectionSpanRasterizer {
    private static final double[] CELL_INSETS = {0.0, -0.25, 0.25};

    private CrossSectionSpanRasterizer() {
    }

    public static void rasterizeSpan(
            RoadCorridorPlan plan,
            Vec2d fromCenter,
            Vec2d toCenter,
            Vec2d fromNormal,
            Vec2d toNormal,
            int fromY,
            int toY,
            double scale,
            ResolvedCrossSection crossSection,
            String seedKey,
            RoadCorridorPlanBuilder.PlanHost host,
            TerrainSampler terrain,
            RoadConstructionType constructionType,
            int tunnelThreshold) {
        if (plan == null || crossSection == null || host == null || terrain == null) {
            return;
        }
        Vec2d startNormal = normalize(fromNormal);
        Vec2d endNormal = normalize(toNormal);
        double blockScale = scale > 1e-9 ? scale : 1.0;
        int steps = Math.max(1, (int) Math.ceil(toCenter.subtract(fromCenter).length() / blockScale * 2));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vec2d center = fromCenter.lerp(toCenter, t);
            Vec2d normal = normalize(startNormal.lerp(endNormal, t));
            int surfaceY = (int) Math.round(fromY + (toY - fromY) * t);
            stampCrossSection(
                plan,
                center,
                normal,
                surfaceY,
                scale,
                crossSection,
                seedKey,
                host,
                terrain,
                constructionType,
                tunnelThreshold);
        }
    }

    private static void stampCrossSection(
            RoadCorridorPlan plan,
            Vec2d center,
            Vec2d normal,
            int surfaceY,
            double scale,
            ResolvedCrossSection crossSection,
            String seedKey,
            RoadCorridorPlanBuilder.PlanHost host,
            TerrainSampler terrain,
            RoadConstructionType constructionType,
            int tunnelThreshold) {
        for (int lateral = crossSection.minSurfaceLateral(); lateral <= crossSection.maxSurfaceLateral(); lateral++) {
            Optional<ResolvedCrossSection.SurfaceBandSpec> band = crossSection.surfaceBandAtLateral(lateral);
            if (band.isEmpty()) {
                continue;
            }
            int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
            for (double inset : CELL_INSETS) {
            Vec2d planPoint = center.add(normal.multiply((lateral + inset) * scale));
            int worldX = host.worldX(planPoint), worldZ = host.worldZ(planPoint);
            if (worldX == lastX && worldZ == lastZ) continue;
            lastX = worldX;
            lastZ = worldZ;
            ResolvedCrossSection.SurfaceBandSpec spec = band.get();
            if (spec.materialMix() != null) {
                BlockPos pos = host.canvasToBlockPos(planPoint).withY(surfaceY);
                String materialId = MaterialMixResolver.resolve(
                    spec.materialMix(), pos, seedKey, host::resolveBlockId);
                RoadCorridorPlanBuilder.writeSurfaceAndClearance(
                    plan, planPoint, surfaceY, spec.layer(), materialId, host,
                    terrain, constructionType, tunnelThreshold);
            } else {
                String materialId = host.resolveBlockId(spec.materialId());
                RoadCorridorPlanBuilder.writeSurfaceAndClearance(
                    plan, planPoint, surfaceY, spec.layer(), materialId, host,
                    terrain, constructionType, tunnelThreshold);
            }
            }
        }
    }

    private static Vec2d normalize(Vec2d vector) {
        if (vector == null || vector.lengthSquared() < 1e-12) {
            return new Vec2d(0, 1);
        }
        return vector.normalize();
    }
}
