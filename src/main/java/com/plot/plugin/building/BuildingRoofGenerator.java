package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.IBlockProjectionService;
import com.plot.core.geometry.polygon.StraightSkeleton;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.building.generation.BuildingBlockWriter;
import com.plot.plugin.building.generation.BuildingCanvasScale;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * 坡屋顶生成器：矩形走精确公式，一般简单多边形走 Straight Skeleton 高度场。
 */
public final class BuildingRoofGenerator {
    private static final double TOLERANCE = 1e-3;

    private BuildingRoofGenerator() {
    }

    public static void generate(
            BuildingGenerationResult result,
            List<Vec2d> outerPoints,
            int topFloorY,
            String roofBlockId,
            BuildingFootprint.RoofType roofType,
            int roofPitchRatio,
            BuildingCanvasScale canvasScale,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionHandler) {
        StraightSkeleton.Result skeleton = StraightSkeleton.compute(outerPoints);
        if (!skeleton.success()) {
            return;
        }

        BuildingCanvasScale scale = canvasScale != null ? canvasScale : BuildingCanvasScale.identity();
        ICoordinateService coords = scale.resolveCoordinates(coordinateService);

        Polygon roofPolygon = BuildingGeometryUtils.toPolygon(outerPoints);
        int pitch = Math.max(1, roofPitchRatio);
        boolean axisAlignedRectangle = BuildingGeometryUtils.isAxisAlignedRectangle(outerPoints, TOLERANCE);
        BuildingGeometryUtils.RectBounds bounds = axisAlignedRectangle
            ? BuildingGeometryUtils.normalizedRectBounds(outerPoints)
            : null;
        boolean ridgeAlongX = bounds != null && bounds.width() >= bounds.depth();

        for (Vec2d center : BuildingGeometryUtils.collectFootprintCellCenters(outerPoints)) {
            if (!roofPolygon.contains(center)) {
                continue;
            }

            int rise = switch (roofType) {
                case GABLE -> computeGableRise(center, outerPoints, skeleton, bounds, ridgeAlongX, pitch, scale);
                case HIP -> computeHipRise(center, skeleton, bounds, pitch, scale);
                default -> 0;
            };

            if (rise <= 0) {
                continue;
            }

            BlockPos column = BuildingGeometryUtils.canvasToBlockXZ(center, coords);
            for (int layer = 1; layer <= rise; layer++) {
                BlockPos pos = new BlockPos(column.getX(), topFloorY + layer, column.getZ());
                BuildingBlockWriter.recordBlock(result, pos, roofBlockId, projectionHandler);
            }
        }
    }

    static int computeGableRise(
            Vec2d point,
            List<Vec2d> outerPoints,
            StraightSkeleton.Result skeleton,
            BuildingGeometryUtils.RectBounds bounds,
            boolean ridgeAlongX,
            int pitch) {
        return computeGableRise(point, outerPoints, skeleton, bounds, ridgeAlongX, pitch, null);
    }

    static int computeGableRise(
            Vec2d point,
            List<Vec2d> outerPoints,
            StraightSkeleton.Result skeleton,
            BuildingGeometryUtils.RectBounds bounds,
            boolean ridgeAlongX,
            int pitch,
            BuildingCanvasScale canvasScale) {
        if (bounds != null) {
            return computeGableRise(point.x, point.y, bounds, ridgeAlongX, pitch, canvasScale);
        }
        BuildingCanvasScale scale = canvasScale != null ? canvasScale : BuildingCanvasScale.identity();
        Vec2d ridgeDirection = skeleton.primaryRidgeDirection();
        double canvasDistance = skeleton.gableEaveDistance(point, ridgeDirection);
        double worldDistance = scale.canvasToBlocks(canvasDistance, point, ridgeDirection);
        return riseFromEaveDistance(worldDistance, pitch);
    }

    static int computeHipRise(
            Vec2d point,
            StraightSkeleton.Result skeleton,
            BuildingGeometryUtils.RectBounds bounds,
            int pitch) {
        return computeHipRise(point, skeleton, bounds, pitch, null);
    }

    static int computeHipRise(
            Vec2d point,
            StraightSkeleton.Result skeleton,
            BuildingGeometryUtils.RectBounds bounds,
            int pitch,
            BuildingCanvasScale canvasScale) {
        if (bounds != null) {
            return computeHipRise(point.x, point.y, bounds, pitch, canvasScale);
        }
        BuildingCanvasScale scale = canvasScale != null ? canvasScale : BuildingCanvasScale.identity();
        double canvasDistance = skeleton.skeletalTime(point);
        Vec2d direction = inwardFromNearestEdge(point, skeleton);
        double worldDistance = scale.canvasToBlocks(canvasDistance, point, direction);
        return riseFromEaveDistance(worldDistance, pitch);
    }

    /**
     * 双坡顶：檐口 rise=0，屋脊 rise 最大。坡度按垂直于屋脊方向的檐口距离计算。
     */
    static int computeGableRise(
            double x,
            double z,
            BuildingGeometryUtils.RectBounds bounds,
            boolean ridgeAlongX,
            int pitch) {
        return computeGableRise(x, z, bounds, ridgeAlongX, pitch, null);
    }

    public static int computeGableRise(
            double x,
            double z,
            BuildingGeometryUtils.RectBounds bounds,
            boolean ridgeAlongX,
            int pitch,
            BuildingCanvasScale canvasScale) {
        BuildingCanvasScale scale = canvasScale != null ? canvasScale : BuildingCanvasScale.identity();
        Vec2d point = new Vec2d(x, z);
        double canvasDistance = ridgeAlongX
            ? Math.min(z - bounds.minZ(), bounds.maxZ() - z)
            : Math.min(x - bounds.minX(), bounds.maxX() - x);
        Vec2d direction = ridgeAlongX ? new Vec2d(0, 1) : new Vec2d(1, 0);
        double worldDistance = scale.canvasToBlocks(canvasDistance, point, direction);
        return riseFromEaveDistance(worldDistance, pitch);
    }

    /**
     * 四坡顶：四角檐口 rise=0，中心屋脊/屋脊线 rise 最大。取到四条边最近距离控制坡度。
     */
    static int computeHipRise(
            double x,
            double z,
            BuildingGeometryUtils.RectBounds bounds,
            int pitch) {
        return computeHipRise(x, z, bounds, pitch, null);
    }

    static int computeHipRise(
            double x,
            double z,
            BuildingGeometryUtils.RectBounds bounds,
            int pitch,
            BuildingCanvasScale canvasScale) {
        BuildingCanvasScale scale = canvasScale != null ? canvasScale : BuildingCanvasScale.identity();
        Vec2d point = new Vec2d(x, z);
        double dx = Math.min(x - bounds.minX(), bounds.maxX() - x);
        double dz = Math.min(z - bounds.minZ(), bounds.maxZ() - z);
        double canvasDistance = Math.min(dx, dz);
        Vec2d direction = dx <= dz ? new Vec2d(1, 0) : new Vec2d(0, 1);
        double worldDistance = scale.canvasToBlocks(canvasDistance, point, direction);
        return riseFromEaveDistance(worldDistance, pitch);
    }

    static int riseFromEaveDistance(double distToEaveBlocks, int pitch) {
        if (distToEaveBlocks <= 0.0) {
            return 0;
        }
        return (int) Math.floor(distToEaveBlocks / pitch);
    }

    private static Vec2d inwardFromNearestEdge(Vec2d point, StraightSkeleton.Result skeleton) {
        Vec2d ridgeDirection = skeleton.primaryRidgeDirection();
        if (ridgeDirection != null && ridgeDirection.lengthSquared() > 1e-12) {
            return ridgeDirection.normalize();
        }
        return new Vec2d(1, 0);
    }
}
