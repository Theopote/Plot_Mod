package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.IBlockProjectionService;
import com.plot.core.geometry.polygon.StraightSkeleton;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.building.generation.BuildingBlockWriter;
import com.plot.plugin.building.generation.BuildingCanvasScale;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.BuildingGridAlignment;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Objects;

/**
 * 坡屋顶生成器：矩形走精确公式，一般简单多边形走 Straight Skeleton 高度场。
 * <p>
 * 坡顶从「屋檐」水平带内侧起坡：距外轮廓 {@code eaves} 格以内为平檐（1 层），再按坡度抬升。
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
            int roofEaves,
            BuildingCanvasScale canvasScale,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionHandler) {
        StraightSkeleton.Result skeleton = StraightSkeleton.compute(outerPoints);
        if (!skeleton.success()) {
            return;
        }

        BuildingCanvasScale scale = Objects.requireNonNull(canvasScale, "canvasScale");
        ICoordinateService coords = scale.resolveCoordinates(coordinateService);

        Polygon roofPolygon = BuildingGeometryUtils.toPolygon(outerPoints);
        int pitch = Math.max(1, roofPitchRatio);
        int eaves = Math.max(0, roofEaves);
        boolean axisAlignedRectangle = BuildingGeometryUtils.isAxisAlignedRectangle(outerPoints, TOLERANCE);
        BuildingGeometryUtils.RectBounds bounds = axisAlignedRectangle
            ? BuildingGeometryUtils.normalizedRectBounds(outerPoints)
            : null;
        boolean ridgeAlongX = bounds != null && bounds.width() >= bounds.depth();

        for (Vec2d center : BuildingGridAlignment.collectWorldAlignedCellCenters(
                outerPoints, roofPolygon, scale)) {
            double eaveDistance = switch (roofType) {
                case GABLE -> computeGableEaveDistance(
                    center, outerPoints, skeleton, bounds, ridgeAlongX, scale);
                case HIP -> computeHipEaveDistance(center, skeleton, bounds, scale);
                default -> 0.0;
            };
            int layers = roofLayerCount(eaveDistance, pitch, eaves);
            if (layers <= 0) {
                continue;
            }

            Vec2d aligned = BuildingGridAlignment.snapToBlockCellCenter(center, scale, outerPoints);
            BlockPos column = BuildingGeometryUtils.canvasToBlockXZ(aligned, coords);
            for (int layer = 1; layer <= layers; layer++) {
                BlockPos pos = new BlockPos(column.getX(), topFloorY + layer, column.getZ());
                BuildingBlockWriter.recordBlock(result, pos, roofBlockId, projectionHandler);
            }
        }
    }

    static int roofLayerCount(double distToEaveBlocks, int pitch, int eavesBlocks) {
        int pitchValue = Math.max(1, pitch);
        int eaves = Math.max(0, eavesBlocks);
        if (distToEaveBlocks <= 0.0) {
            return 0;
        }
        if (eaves > 0 && distToEaveBlocks <= eaves) {
            return 1;
        }
        if (eaves > 0) {
            return 1 + (int) Math.floor((distToEaveBlocks - eaves) / pitchValue);
        }
        // 外轮廓最外圈格心距檐口可能不足 1 格，仍应铺 1 层屋面
        return Math.max(1, (int) Math.floor(distToEaveBlocks / pitchValue));
    }

    static double computeGableEaveDistance(
            Vec2d point,
            List<Vec2d> outerPoints,
            StraightSkeleton.Result skeleton,
            BuildingGeometryUtils.RectBounds bounds,
            boolean ridgeAlongX,
            BuildingCanvasScale canvasScale) {
        if (bounds != null) {
            return computeGableEaveDistance(point.x, point.y, bounds, ridgeAlongX, canvasScale);
        }
        BuildingCanvasScale scale = Objects.requireNonNull(canvasScale, "canvasScale");
        Vec2d ridgeDirection = skeleton.primaryRidgeDirection();
        double canvasDistance = skeleton.gableEaveDistance(point, ridgeDirection);
        return scale.canvasToBlocks(canvasDistance, point, ridgeDirection);
    }

    static int computeHipRise(
            Vec2d point,
            StraightSkeleton.Result skeleton,
            BuildingGeometryUtils.RectBounds bounds,
            int pitch,
            BuildingCanvasScale canvasScale) {
        return computeHipRise(point, skeleton, bounds, pitch, canvasScale, 0);
    }

    static int computeHipRise(
            Vec2d point,
            StraightSkeleton.Result skeleton,
            BuildingGeometryUtils.RectBounds bounds,
            int pitch,
            BuildingCanvasScale canvasScale,
            int eavesBlocks) {
        return roofLayerCount(
            computeHipEaveDistance(point, skeleton, bounds, canvasScale),
            pitch,
            eavesBlocks);
    }

    static double computeHipEaveDistance(
            Vec2d point,
            StraightSkeleton.Result skeleton,
            BuildingGeometryUtils.RectBounds bounds,
            BuildingCanvasScale canvasScale) {
        if (bounds != null) {
            return computeHipEaveDistance(point.x, point.y, bounds, canvasScale);
        }
        BuildingCanvasScale scale = Objects.requireNonNull(canvasScale, "canvasScale");
        double canvasDistance = skeleton.skeletalTime(point);
        Vec2d direction = inwardFromNearestEdge(point, skeleton);
        return scale.canvasToBlocks(canvasDistance, point, direction);
    }

    public static int computeGableRise(
            double x,
            double z,
            BuildingGeometryUtils.RectBounds bounds,
            boolean ridgeAlongX,
            int pitch,
            BuildingCanvasScale canvasScale) {
        return computeGableRise(x, z, bounds, ridgeAlongX, pitch, canvasScale, 0);
    }

    public static int computeGableRise(
            double x,
            double z,
            BuildingGeometryUtils.RectBounds bounds,
            boolean ridgeAlongX,
            int pitch,
            BuildingCanvasScale canvasScale,
            int eavesBlocks) {
        return roofLayerCount(
            computeGableEaveDistance(x, z, bounds, ridgeAlongX, canvasScale),
            pitch,
            eavesBlocks);
    }

    static double computeGableEaveDistance(
            double x,
            double z,
            BuildingGeometryUtils.RectBounds bounds,
            boolean ridgeAlongX,
            BuildingCanvasScale canvasScale) {
        BuildingCanvasScale scale = Objects.requireNonNull(canvasScale, "canvasScale");
        Vec2d point = new Vec2d(x, z);
        double canvasDistance = ridgeAlongX
            ? Math.min(z - bounds.minZ(), bounds.maxZ() - z)
            : Math.min(x - bounds.minX(), bounds.maxX() - x);
        Vec2d direction = ridgeAlongX ? new Vec2d(0, 1) : new Vec2d(1, 0);
        return scale.canvasToBlocks(canvasDistance, point, direction);
    }


    static int computeHipRise(
            double x,
            double z,
            BuildingGeometryUtils.RectBounds bounds,
            int pitch,
            BuildingCanvasScale canvasScale) {
        return computeHipRise(x, z, bounds, pitch, canvasScale, 0);
    }

    static int computeHipRise(
            double x,
            double z,
            BuildingGeometryUtils.RectBounds bounds,
            int pitch,
            BuildingCanvasScale canvasScale,
            int eavesBlocks) {
        return roofLayerCount(
            computeHipEaveDistance(x, z, bounds, canvasScale),
            pitch,
            eavesBlocks);
    }

    static double computeHipEaveDistance(
            double x,
            double z,
            BuildingGeometryUtils.RectBounds bounds,
            BuildingCanvasScale canvasScale) {
        BuildingCanvasScale scale = Objects.requireNonNull(canvasScale, "canvasScale");
        Vec2d point = new Vec2d(x, z);
        double dx = Math.min(x - bounds.minX(), bounds.maxX() - x);
        double dz = Math.min(z - bounds.minZ(), bounds.maxZ() - z);
        double canvasDistance = Math.min(dx, dz);
        Vec2d direction = dx <= dz ? new Vec2d(1, 0) : new Vec2d(0, 1);
        return scale.canvasToBlocks(canvasDistance, point, direction);
    }

    /** @deprecated use {@link #roofLayerCount} */
    static int riseFromEaveDistance(double distToEaveBlocks, int pitch) {
        return roofLayerCount(distToEaveBlocks, pitch, 0);
    }

    private static Vec2d inwardFromNearestEdge(Vec2d point, StraightSkeleton.Result skeleton) {
        Vec2d ridgeDirection = skeleton.primaryRidgeDirection();
        if (ridgeDirection != null && ridgeDirection.lengthSquared() > 1e-12) {
            return ridgeDirection.normalize();
        }
        return new Vec2d(1, 0);
    }
}
