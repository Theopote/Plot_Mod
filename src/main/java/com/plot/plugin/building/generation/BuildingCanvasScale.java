package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PluginProjectionContext;
import com.plot.core.geometry.WorldProjectionMath;
import com.plot.core.geometry.polygon.PolygonOffset;
import com.plot.plugin.building.model.spec.FloorPlateSpec;

import java.util.List;
import java.util.Objects;

/**
 * 建筑生成用的画布尺度：将「方块数」参数转换为画布距离。
 * <p>
 * 生成入口应通过 {@link #capture(ICoordinateService, List)} 冻结投影，
 * 全程复用同一实例。
 */
public final class BuildingCanvasScale {
    private final ICoordinateService coordinates;

    private BuildingCanvasScale(ICoordinateService coordinates) {
        this.coordinates = coordinates;
    }

    public static BuildingCanvasScale capture(ICoordinateService coordinateService, List<Vec2d> outerPoints) {
        Objects.requireNonNull(coordinateService, "coordinateService");
        return PluginProjectionContext.tryCapture(coordinateService)
            .map(ctx -> new BuildingCanvasScale(ctx.coordinates()))
            .orElse(new BuildingCanvasScale(coordinateService));
    }

    /** 1 block = 1 canvas unit（测试或投影不可用时）。 */
    public static BuildingCanvasScale identity() {
        return new BuildingCanvasScale(null);
    }

    /** 生成全程应使用的冻结坐标服务；identity 时回退到调用方提供的 live 服务。 */
    public ICoordinateService resolveCoordinates(ICoordinateService liveCoordinates) {
        return coordinates != null ? coordinates : liveCoordinates;
    }

    public double blocksToCanvas(double blocks, Vec2d origin, Vec2d direction) {
        if (blocks == 0.0) {
            return 0.0;
        }
        if (coordinates == null) {
            return blocks;
        }
        return blocks * WorldProjectionMath.canvasUnitsPerWorldBlock(coordinates, origin, direction);
    }

    /** 画布距离 → 世界方块数（沿给定方向）。 */
    public double canvasToBlocks(double canvasDistance, Vec2d origin, Vec2d direction) {
        if (canvasDistance == 0.0) {
            return 0.0;
        }
        if (coordinates == null) {
            return canvasDistance;
        }
        double unitsPerBlock = WorldProjectionMath.canvasUnitsPerWorldBlock(coordinates, origin, direction);
        if (unitsPerBlock < 1e-9) {
            return canvasDistance;
        }
        return canvasDistance / unitsPerBlock;
    }

    /**
     * 从基础 footprint 均匀内缩指定方块数，生成退台 FloorPlate。
     */
    public FloorPlateSpec insetFloorPlate(
            int floorStart,
            int floorEnd,
            List<Vec2d> baseFootprint,
            double insetBlocks) {
        double canvasInset = uniformBlocksToCanvas(insetBlocks, baseFootprint);
        PolygonOffset.OffsetResult result = PolygonOffset.offsetInward(baseFootprint, canvasInset);
        if (!result.success() || result.points().size() < 3) {
            throw new IllegalArgumentException("inset produced invalid floor plate");
        }
        return new FloorPlateSpec(floorStart, floorEnd, result.points());
    }

    /**
     * 多边形均匀内缩/外扩：各边方向上的尺度取平均。
     */
    public double uniformBlocksToCanvas(double blocks, List<Vec2d> outerPoints) {
        if (blocks == 0.0) {
            return 0.0;
        }
        if (coordinates == null || outerPoints == null || outerPoints.size() < 3) {
            return blocks;
        }
        Vec2d probe = centroid(outerPoints);
        double sum = 0.0;
        int n = outerPoints.size();
        for (int i = 0; i < n; i++) {
            Vec2d start = outerPoints.get(i);
            Vec2d end = outerPoints.get((i + 1) % n);
            sum += WorldProjectionMath.canvasUnitsPerWorldBlock(coordinates, probe, end.subtract(start));
        }
        return blocks * (sum / n);
    }

    private static Vec2d centroid(List<Vec2d> points) {
        double sx = 0.0;
        double sy = 0.0;
        for (Vec2d point : points) {
            sx += point.x;
            sy += point.y;
        }
        return new Vec2d(sx / points.size(), sy / points.size());
    }
}
