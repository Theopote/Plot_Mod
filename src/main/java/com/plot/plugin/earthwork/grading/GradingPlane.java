package com.plot.plugin.earthwork.grading;
import com.plot.plugin.earthwork.design.GradingSurfaceResolver;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.VoxelElevationDiscretizer;
import com.plot.plugin.earthwork.model.GradingRegion;

import java.util.List;

/**
 * 整平目标平面：worldY = coeffX * worldX + coeffZ * worldZ + intercept。
 * <p>
 * 坡面求值经 {@link VoxelElevationDiscretizer} 离散，避免逐格 {@code Math.round()} 造成不均匀台阶。
 */
public record GradingPlane(double coeffX, double coeffZ, double intercept, VoxelProfile voxelProfile) {

    public GradingPlane(double coeffX, double coeffZ, double intercept) {
        this(coeffX, coeffZ, intercept, null);
    }

    public static GradingPlane flat(int elevation) {
        return new GradingPlane(0.0, 0.0, elevation);
    }

    public int evaluateAt(int worldX, int worldZ) {
        if (isFlat()) {
            return VoxelElevationDiscretizer.quantizeContinuous(intercept);
        }
        if (voxelProfile != null) {
            return voxelProfile.evaluateAt(worldX, worldZ);
        }
        return discretizeFromOrigin(worldX, worldZ);
    }

    public double evaluateAtExact(double worldX, double worldZ) {
        return coeffX * worldX + coeffZ * worldZ + intercept;
    }

    public boolean isFlat() {
        return Math.abs(coeffX) < 1e-9 && Math.abs(coeffZ) < 1e-9;
    }

    public static GradingPlane withVoxelDiscretization(
            GradingPlane plane,
            GradingRegion region,
            List<GradingSurfaceResolver.HeightSample> samples,
            ICoordinateService transformer) {
        if (plane == null || plane.isFlat() || region == null) {
            return plane;
        }
        double gradientMagnitude = Math.hypot(plane.coeffX, plane.coeffZ);
        if (gradientMagnitude < 1e-9) {
            return plane;
        }

        Anchor anchor = resolveAnchor(region, samples, transformer, plane);
        double unitGradX = plane.coeffX / gradientMagnitude;
        double unitGradZ = plane.coeffZ / gradientMagnitude;
        double anchorElevation = plane.evaluateAtExact(anchor.worldX, anchor.worldZ);

        double minProjection = Double.POSITIVE_INFINITY;
        double maxProjection = Double.NEGATIVE_INFINITY;
        for (Vec2d canvasPoint : region.getOuterPoints()) {
            var block = EarthworkGeometryUtils.canvasToBlockXZ(canvasPoint, transformer);
            double projection = projectOntoGradient(
                block.getX(), block.getZ(), anchor.worldX, anchor.worldZ, unitGradX, unitGradZ);
            minProjection = Math.min(minProjection, projection);
            maxProjection = Math.max(maxProjection, projection);
        }
        if (samples != null) {
            for (GradingSurfaceResolver.HeightSample sample : samples) {
                double projection = projectOntoGradient(
                    sample.worldX(), sample.worldZ(), anchor.worldX, anchor.worldZ, unitGradX, unitGradZ);
                minProjection = Math.min(minProjection, projection);
                maxProjection = Math.max(maxProjection, projection);
            }
        }
        if (!Double.isFinite(minProjection) || !Double.isFinite(maxProjection)) {
            return plane;
        }

        int[] elevations = VoxelElevationDiscretizer.discretizeLinearSlope(
            anchorElevation, gradientMagnitude, minProjection, maxProjection);
        if (elevations.length == 0) {
            return plane;
        }
        VoxelProfile profile = new VoxelProfile(
            anchor.worldX,
            anchor.worldZ,
            unitGradX,
            unitGradZ,
            minProjection,
            elevations);
        return new GradingPlane(plane.coeffX, plane.coeffZ, plane.intercept, profile);
    }

    private static Anchor resolveAnchor(
            GradingRegion region,
            List<GradingSurfaceResolver.HeightSample> samples,
            ICoordinateService transformer,
            GradingPlane plane) {
        Vec2d anchorCanvas = region.getSlopeAnchorCanvas();
        if (anchorCanvas == null) {
            anchorCanvas = EarthworkGeometryUtils.computeCentroid(region.getOuterPoints());
        }
        var anchorBlock = EarthworkGeometryUtils.canvasToBlockXZ(anchorCanvas, transformer);
        return new Anchor(anchorBlock.getX(), anchorBlock.getZ());
    }

    private int discretizeFromOrigin(int worldX, int worldZ) {
        double gradientMagnitude = Math.hypot(coeffX, coeffZ);
        double unitGradX = coeffX / gradientMagnitude;
        double unitGradZ = coeffZ / gradientMagnitude;
        double anchorElevation = evaluateAtExact(0.0, 0.0);
        double projection = worldX * unitGradX + worldZ * unitGradZ;
        if (projection <= 0.0) {
            return VoxelElevationDiscretizer.quantizeContinuous(anchorElevation + projection * gradientMagnitude);
        }
        List<Integer> profile = VoxelElevationDiscretizer.discretizeContinuousProfile(
            projection,
            station -> anchorElevation + station * gradientMagnitude);
        return profile.get(profile.size() - 1);
    }

    private static double projectOntoGradient(
            double worldX,
            double worldZ,
            double anchorX,
            double anchorZ,
            double unitGradX,
            double unitGradZ) {
        return (worldX - anchorX) * unitGradX + (worldZ - anchorZ) * unitGradZ;
    }

    private record Anchor(double worldX, double worldZ) {
    }

    public record VoxelProfile(
            double anchorWorldX,
            double anchorWorldZ,
            double unitGradX,
            double unitGradZ,
            double originProjection,
            int[] elevations) {

        public int evaluateAt(int worldX, int worldZ) {
            double projection = (worldX - anchorWorldX) * unitGradX + (worldZ - anchorWorldZ) * unitGradZ;
            double station = projection - originProjection;
            return VoxelElevationDiscretizer.elevationAtStation(station, elevations);
        }
    }
}
