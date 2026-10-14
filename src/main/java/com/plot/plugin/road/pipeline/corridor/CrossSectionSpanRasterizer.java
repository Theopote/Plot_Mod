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
        List<Vec2d> centerline = RoadVoxelRasterizer.sampleSpanPoints(fromCenter, toCenter);
        int last = centerline.size() - 1;
        for (int i = 0; i < centerline.size(); i++) {
            Vec2d center = centerline.get(i);
            double t = last == 0 ? 0.0 : (double) i / last;
            Vec2d normal = normalize(startNormal.lerp(endNormal, t));
            int surfaceY = last == 0
                ? fromY
                : (int) Math.round(fromY + (toY - fromY) * t);
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
            Vec2d planPoint = center.add(normal.multiply(lateral * scale));
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

    private static Vec2d normalize(Vec2d vector) {
        if (vector == null || vector.lengthSquared() < 1e-12) {
            return new Vec2d(0, 1);
        }
        return vector.normalize();
    }
}
