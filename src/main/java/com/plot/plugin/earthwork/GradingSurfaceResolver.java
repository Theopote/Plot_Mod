package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingSurfaceMode;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据区域参数与地形采样，解析整平目标平面。
 */
public final class GradingSurfaceResolver {

    private GradingSurfaceResolver() {
    }

    public record HeightSample(int worldX, int worldZ, int groundY) {
    }

    public record ResolvedSurface(GradingPlane plane, int elevationMin, int elevationMax) {
    }

    public static ResolvedSurface resolve(
            GradingRegion region,
            List<Vec2d> sampleCenters,
            List<Integer> sampleHeights,
            CoordinateTransformer transformer) {
        List<HeightSample> samples = buildSamples(sampleCenters, sampleHeights, transformer);
        GradingPlane plane = switch (region.getSurfaceMode()) {
            case FLAT -> resolveFlat(region, samples);
            case FIXED_SLOPE -> resolveFixedSlope(region, samples, transformer);
            case THREE_POINT -> resolveThreePoint(region, transformer);
            case FIT_SLOPE -> resolveFitSlope(region, samples);
        };
        return summarize(plane, samples);
    }

    public static void initializeThreePointDefaults(
            GradingRegion region,
            List<Vec2d> sampleCenters,
            List<Integer> sampleHeights,
            CoordinateTransformer transformer) {
        if (region == null || sampleCenters == null || sampleCenters.isEmpty()) {
            return;
        }
        List<Vec2d> points = region.getOuterPoints();
        if (points.size() < 3) {
            return;
        }

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vec2d point : points) {
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minZ = Math.min(minZ, point.y);
            maxZ = Math.max(maxZ, point.y);
        }

        Vec2d corner1 = new Vec2d(minX, minZ);
        Vec2d corner2 = new Vec2d(maxX, minZ);
        Vec2d corner3 = new Vec2d(minX, maxZ);
        List<HeightSample> samples = buildSamples(sampleCenters, sampleHeights, transformer);

        region.setThreePointControl(0, corner1, nearestSampleElevation(corner1, samples, transformer, 64));
        region.setThreePointControl(1, corner2, nearestSampleElevation(corner2, samples, transformer, 64));
        region.setThreePointControl(2, corner3, nearestSampleElevation(corner3, samples, transformer, 64));
    }

    public static void initializeFixedSlopeDefaults(
            GradingRegion region,
            List<Vec2d> sampleCenters,
            List<Integer> sampleHeights,
            CoordinateTransformer transformer) {
        if (region == null) {
            return;
        }
        Vec2d centroid = EarthworkGeometryUtils.computeCentroid(region.getOuterPoints());
        List<HeightSample> samples = buildSamples(sampleCenters, sampleHeights, transformer);
        region.setSlopeAnchorCanvas(centroid);
        if (region.getSlopeAnchorElevation() == null) {
            region.setSlopeAnchorElevation(nearestSampleElevation(centroid, samples, transformer, 64));
        }
    }

    private static GradingPlane resolveFlat(GradingRegion region, List<HeightSample> samples) {
        int elevation;
        if (region.isAutoBalance()) {
            elevation = EarthworkBalanceUtils.findBalancedElevation(sampleHeights(samples), region.getFillFactor());
        } else if (region.getManualTargetElevation() != null) {
            elevation = region.getManualTargetElevation();
        } else {
            elevation = EarthworkBalanceUtils.findBalancedElevation(sampleHeights(samples), region.getFillFactor());
        }
        return GradingPlane.flat(elevation);
    }

    private static GradingPlane resolveFixedSlope(
            GradingRegion region,
            List<HeightSample> samples,
            CoordinateTransformer transformer) {
        Vec2d anchorCanvas = region.getSlopeAnchorCanvas();
        if (anchorCanvas == null) {
            anchorCanvas = EarthworkGeometryUtils.computeCentroid(region.getOuterPoints());
        }
        var anchorBlock = EarthworkGeometryUtils.canvasToBlockXZ(anchorCanvas, transformer);
        double anchorX = anchorBlock.getX();
        double anchorZ = anchorBlock.getZ();
        int anchorElevation = region.getSlopeAnchorElevation() != null
            ? region.getSlopeAnchorElevation()
            : nearestSampleElevation(anchorCanvas, samples, transformer, 64);

        double radians = Math.toRadians(region.getSlopeDirectionDegrees());
        double runPerRise = Math.max(1, region.getSlopePitchRatio());
        double coeffX = Math.cos(radians) / runPerRise;
        double coeffZ = Math.sin(radians) / runPerRise;
        double intercept = anchorElevation - coeffX * anchorX - coeffZ * anchorZ;
        return new GradingPlane(coeffX, coeffZ, intercept);
    }

    private static GradingPlane resolveThreePoint(GradingRegion region, CoordinateTransformer transformer) {
        var block1 = EarthworkGeometryUtils.canvasToBlockXZ(
            new Vec2d(region.getThreePointCanvasX(0), region.getThreePointCanvasY(0)), transformer);
        var block2 = EarthworkGeometryUtils.canvasToBlockXZ(
            new Vec2d(region.getThreePointCanvasX(1), region.getThreePointCanvasY(1)), transformer);
        var block3 = EarthworkGeometryUtils.canvasToBlockXZ(
            new Vec2d(region.getThreePointCanvasX(2), region.getThreePointCanvasY(2)), transformer);

        double x1 = block1.getX();
        double z1 = block1.getZ();
        int y1 = region.getThreePointElevation(0);
        double x2 = block2.getX();
        double z2 = block2.getZ();
        int y2 = region.getThreePointElevation(1);
        double x3 = block3.getX();
        double z3 = block3.getZ();
        int y3 = region.getThreePointElevation(2);

        GradingPlane plane = solveThreePointPlane(x1, z1, y1, x2, z2, y2, x3, z3, y3);
        if (plane != null) {
            return plane;
        }
        return GradingPlane.flat(Math.round((y1 + y2 + y3) / 3.0f));
    }

    private static GradingPlane resolveFitSlope(GradingRegion region, List<HeightSample> samples) {
        if (samples.isEmpty()) {
            return GradingPlane.flat(64);
        }
        GradingPlane leastSquares = fitLeastSquaresPlane(samples);
        if (!region.isFitSlopeBalanceCutFill()) {
            return leastSquares;
        }
        return balancePlaneIntercept(leastSquares, samples, region.getFillFactor());
    }

    static GradingPlane solveThreePointPlane(
            double x1, double z1, int y1,
            double x2, double z2, int y2,
            double x3, double z3, int y3) {
        double a23 = z2 - z3;
        double a31 = z3 - z1;
        double a12 = z1 - z2;
        double b23 = x3 - x2;
        double b31 = x1 - x3;
        double b12 = x2 - x1;
        double det = x1 * a23 + x2 * a31 + x3 * a12;
        if (Math.abs(det) < 1e-6) {
            return null;
        }
        double coeffX = (y1 * a23 + y2 * a31 + y3 * a12) / det;
        double coeffZ = (y1 * b23 + y2 * b31 + y3 * b12) / det;
        double intercept = (y1 * (x2 * z3 - x3 * z2) + y2 * (x3 * z1 - x1 * z3) + y3 * (x1 * z2 - x2 * z1)) / det;
        return new GradingPlane(coeffX, coeffZ, intercept);
    }

    static GradingPlane fitLeastSquaresPlane(List<HeightSample> samples) {
        double sumX = 0;
        double sumZ = 0;
        double sumY = 0;
        double sumXX = 0;
        double sumZZ = 0;
        double sumXZ = 0;
        double sumXY = 0;
        double sumZY = 0;
        int n = samples.size();
        for (HeightSample sample : samples) {
            double x = sample.worldX();
            double z = sample.worldZ();
            double y = sample.groundY();
            sumX += x;
            sumZ += z;
            sumY += y;
            sumXX += x * x;
            sumZZ += z * z;
            sumXZ += x * z;
            sumXY += x * y;
            sumZY += z * y;
        }

        double[][] matrix = {
            {sumXX, sumXZ, sumX},
            {sumXZ, sumZZ, sumZ},
            {sumX, sumZ, (double) n}
        };
        double[] rhs = {sumXY, sumZY, sumY};
        double[] solution = solveSymmetric3x3(matrix, rhs);
        if (solution == null) {
            int average = (int) Math.round(sumY / n);
            return GradingPlane.flat(average);
        }
        return new GradingPlane(solution[0], solution[1], solution[2]);
    }

    private static GradingPlane balancePlaneIntercept(
            GradingPlane basePlane,
            List<HeightSample> samples,
            float fillFactor) {
        List<Integer> targetHeights = new ArrayList<>(samples.size());
        for (HeightSample sample : samples) {
            targetHeights.add(basePlane.evaluateAt(sample.worldX(), sample.worldZ()));
        }
        int balanced = EarthworkBalanceUtils.findBalancedElevation(targetHeights, fillFactor);
        int currentAverage = (int) Math.round(targetHeights.stream().mapToInt(Integer::intValue).average().orElse(balanced));
        int delta = balanced - currentAverage;
        return new GradingPlane(basePlane.coeffX(), basePlane.coeffZ(), basePlane.intercept() + delta);
    }

    private static ResolvedSurface summarize(GradingPlane plane, List<HeightSample> samples) {
        if (samples.isEmpty()) {
            int elevation = plane.evaluateAt(0, 0);
            return new ResolvedSurface(plane, elevation, elevation);
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (HeightSample sample : samples) {
            int target = plane.evaluateAt(sample.worldX(), sample.worldZ());
            min = Math.min(min, target);
            max = Math.max(max, target);
        }
        return new ResolvedSurface(plane, min, max);
    }

    private static List<HeightSample> buildSamples(
            List<Vec2d> sampleCenters,
            List<Integer> sampleHeights,
            CoordinateTransformer transformer) {
        List<HeightSample> samples = new ArrayList<>();
        if (sampleCenters == null || sampleHeights == null) {
            return samples;
        }
        int count = Math.min(sampleCenters.size(), sampleHeights.size());
        for (int i = 0; i < count; i++) {
            Vec2d center = sampleCenters.get(i);
            var blockPos = EarthworkGeometryUtils.canvasToBlockXZ(center, transformer);
            samples.add(new HeightSample(blockPos.getX(), blockPos.getZ(), sampleHeights.get(i)));
        }
        return samples;
    }

    private static List<Integer> sampleHeights(List<HeightSample> samples) {
        return samples.stream().map(HeightSample::groundY).toList();
    }

    private static int nearestSampleElevation(
            Vec2d canvasPoint,
            List<HeightSample> samples,
            CoordinateTransformer transformer,
            int fallback) {
        if (canvasPoint == null || samples.isEmpty()) {
            return fallback;
        }
        double targetX = canvasPoint.x;
        double targetZ = canvasPoint.y;
        if (transformer != null) {
            var blockPos = EarthworkGeometryUtils.canvasToBlockXZ(canvasPoint, transformer);
            targetX = blockPos.getX();
            targetZ = blockPos.getZ();
        }
        double bestDistance = Double.MAX_VALUE;
        int bestHeight = fallback;
        for (HeightSample sample : samples) {
            double dx = sample.worldX() - targetX;
            double dz = sample.worldZ() - targetZ;
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestHeight = sample.groundY();
            }
        }
        return bestHeight;
    }

    private static double[] solveSymmetric3x3(double[][] matrix, double[] rhs) {
        double det = determinant3x3(matrix);
        if (Math.abs(det) < 1e-9) {
            return null;
        }
        double[] solution = new double[3];
        for (int i = 0; i < 3; i++) {
            double[][] replaced = replaceColumn(matrix, rhs, i);
            solution[i] = determinant3x3(replaced) / det;
        }
        return solution;
    }

    private static double[][] replaceColumn(double[][] matrix, double[] column, int index) {
        double[][] copy = new double[3][3];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                copy[row][col] = col == index ? column[row] : matrix[row][col];
            }
        }
        return copy;
    }

    private static double determinant3x3(double[][] matrix) {
        return matrix[0][0] * (matrix[1][1] * matrix[2][2] - matrix[1][2] * matrix[2][1])
            - matrix[0][1] * (matrix[1][0] * matrix[2][2] - matrix[1][2] * matrix[2][0])
            + matrix[0][2] * (matrix[1][0] * matrix[2][1] - matrix[1][1] * matrix[2][0]);
    }
}
