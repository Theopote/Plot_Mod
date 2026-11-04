package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;

/**
 * Stamps a full cross-section at each centerline sample so adjacent bands share coverage.
 * <p>
 * 锐折处用法向角向插值（勿线性 lerp，否则中间法向会塌缩/翻转），并丢弃越过中心线、
 * 落入本段或邻段行车道包络的人行道/路肩格，避免尖角内侧蓝带插入路面。
 */
public final class CrossSectionSpanRasterizer {
    private static final double[] CELL_INSETS = {0.0, -0.25, 0.25};
    /** 转角超过约 90°（折线内角为锐角）时加密采样并强制防串侧。 */
    private static final double ACUTE_TURN_RADIANS = Math.PI * 0.5;
    /** 显著转角即启用中心线/行车道裁剪（不必等锐角）。 */
    private static final double CLIP_TURN_RADIANS = Math.toRadians(25.0);

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
            int tunnelThreshold,
            List<PathSegment> polylineSegments) {
        if (plan == null || crossSection == null || host == null || terrain == null) {
            return;
        }
        Vec2d startNormal = normalize(fromNormal);
        Vec2d endNormal = normalize(toNormal);
        double turn = signedAngle(startNormal, endNormal);
        boolean rotated = Math.abs(startNormal.x * startNormal.y) > 1e-9
            || Math.abs(endNormal.x * endNormal.y) > 1e-9
            || Math.abs(turn) > 1e-9;
        boolean acute = Math.abs(turn) >= ACUTE_TURN_RADIANS;
        boolean clipIntrusion = acute
            || Math.abs(turn) >= CLIP_TURN_RADIANS
            || (constructionType != null && constructionType.isTunnelFamily())
            || (polylineSegments != null && polylineSegments.size() >= 2);
        var fromWorld = host.canvasToBlockPos(fromCenter);
        var toWorld = host.canvasToBlockPos(toCenter);
        double worldDistance = Math.hypot(toWorld.getX() - fromWorld.getX(), toWorld.getZ() - fromWorld.getZ());
        // Acute turns leave triangular gore gaps between consecutive sections; denser stamps
        // keep pavement and clearance continuous through the inner corner.
        int density = acute ? 5 : (rotated ? 2 : 1);
        int steps = Math.clamp((int) Math.ceil(worldDistance * density), 0, 64);
        // 锐折：端点用各自法向，中段用弦线左法向，避免内外角斜接把外侧条带折进车行道
        Vec2d chordNormal = chordLeftNormal(fromCenter, toCenter, startNormal, endNormal);
        double carriageHalf = Math.max(0.5, crossSection.carriagewayHalfWidth()) * scale;
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            Vec2d center = fromCenter.lerp(toCenter, t);
            Vec2d normal;
            if (acute) {
                if (t <= 1e-9) {
                    normal = startNormal;
                } else if (t >= 1.0 - 1e-9) {
                    normal = endNormal;
                } else {
                    normal = chordNormal;
                }
            } else if (Math.abs(turn) <= 1e-9) {
                normal = endNormal;
            } else {
                normal = normalize(startNormal.rotate(turn * t));
            }
            int surfaceY = (int) Math.round(fromY + (toY - fromY) * t);
            stampCrossSection(
                plan,
                center,
                normal,
                surfaceY,
                scale,
                carriageHalf,
                crossSection,
                seedKey,
                host,
                terrain,
                constructionType,
                tunnelThreshold,
                clipIntrusion,
                polylineSegments);
        }
    }

    private static void stampCrossSection(
            RoadCorridorPlan plan,
            Vec2d center,
            Vec2d normal,
            int surfaceY,
            double scale,
            double carriageHalf,
            ResolvedCrossSection crossSection,
            String seedKey,
            RoadCorridorPlanBuilder.PlanHost host,
            TerrainSampler terrain,
            RoadConstructionType constructionType,
            int tunnelThreshold,
            boolean clipIntrusion,
            List<PathSegment> polylineSegments) {
        for (int lateral = crossSection.minSurfaceLateral(); lateral <= crossSection.maxSurfaceLateral(); lateral++) {
            Optional<ResolvedCrossSection.SurfaceBandSpec> band = crossSection.surfaceBandAtLateral(lateral);
            if (band.isEmpty()) {
                continue;
            }
            ResolvedCrossSection.SurfaceBandSpec spec = band.get();
            int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
            for (double inset : CELL_INSETS) {
                double signedLateral = (lateral + inset) * scale;
                Vec2d planPoint = center.add(normal.multiply(signedLateral));
                if (clipIntrusion && shouldRejectIntrudingStamp(
                        center, normal, planPoint, signedLateral, scale, carriageHalf, spec.layer(),
                        polylineSegments)) {
                    continue;
                }
                int worldX = host.worldX(planPoint), worldZ = host.worldZ(planPoint);
                if (worldX == lastX && worldZ == lastZ) continue;
                lastX = worldX;
                lastZ = worldZ;
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

    /**
     * 丢弃：越过中心线，或外侧条带落入本段/邻段行车道半宽内。
     */
    static boolean shouldRejectIntrudingStamp(
            Vec2d center,
            Vec2d normal,
            Vec2d planPoint,
            double expectedSignedLateral,
            double scale,
            double carriageHalf,
            RoadSolidLayer layer) {
        return shouldRejectIntrudingStamp(
            center, normal, planPoint, expectedSignedLateral, scale, carriageHalf, layer, null);
    }

    static boolean shouldRejectIntrudingStamp(
            Vec2d center,
            Vec2d normal,
            Vec2d planPoint,
            double expectedSignedLateral,
            double scale,
            double carriageHalf,
            RoadSolidLayer layer,
            List<PathSegment> polylineSegments) {
        if (center == null || normal == null || planPoint == null) {
            return false;
        }
        if (crossesCenterline(center, normal, planPoint, expectedSignedLateral, scale)) {
            return true;
        }
        if (!isOuterPavementBand(layer) || Math.abs(expectedSignedLateral) <= 1e-9) {
            return false;
        }
        double projected = planPoint.subtract(center).dot(normal);
        // 预期在行车道外，实际投影却掉进车行道路幅
        if (Math.abs(projected) + 1e-9 < carriageHalf) {
            return true;
        }
        // 邻段（回头弯对侧）行车道包络：本段外侧条带落入邻段车行道（含体素余量）
        return NeighborCarriagewayClip.insideAnyCarriageway(
            planPoint, polylineSegments, carriageHalf, NeighborCarriagewayClip.VOXEL_MARGIN * scale);
    }

    static boolean isOuterPavementBand(RoadSolidLayer layer) {
        return layer == RoadSolidLayer.SIDEWALK
            || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.BIKE_LANE;
    }

    /**
     * 采样点相对中心在法向投影上已翻到另一侧（常见于锐折内角），丢弃以免路肩写入车行道。
     */
    static boolean crossesCenterline(
            Vec2d center,
            Vec2d normal,
            Vec2d planPoint,
            double expectedSignedLateral,
            double scale) {
        if (center == null || normal == null || planPoint == null) {
            return false;
        }
        double projected = planPoint.subtract(center).dot(normal);
        if (expectedSignedLateral * projected >= 0.0) {
            return false;
        }
        return Math.abs(projected) > 0.35 * Math.max(scale, 1e-9);
    }

    private static Vec2d chordLeftNormal(Vec2d fromCenter, Vec2d toCenter, Vec2d startNormal, Vec2d endNormal) {
        Vec2d forward = toCenter.subtract(fromCenter);
        if (forward.lengthSquared() < 1e-12) {
            return startNormal;
        }
        Vec2d unit = forward.normalize();
        Vec2d chord = new Vec2d(-unit.y, unit.x);
        if (chord.dot(startNormal) + chord.dot(endNormal) < 0.0) {
            chord = chord.multiply(-1.0);
        }
        return normalize(chord);
    }

    private static double signedAngle(Vec2d from, Vec2d to) {
        Vec2d a = normalize(from);
        Vec2d b = normalize(to);
        if (a.lengthSquared() < 1e-12 || b.lengthSquared() < 1e-12) {
            return 0.0;
        }
        return Math.atan2(a.cross(b), a.dot(b));
    }

    private static Vec2d normalize(Vec2d vector) {
        if (vector == null || vector.lengthSquared() < 1e-12) {
            return new Vec2d(0, 1);
        }
        return vector.normalize();
    }
}
