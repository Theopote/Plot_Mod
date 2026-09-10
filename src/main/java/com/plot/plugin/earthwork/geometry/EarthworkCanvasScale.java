package com.plot.plugin.earthwork.geometry;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PluginProjectionContext;
import com.plot.core.geometry.WorldProjectionMath;

import java.util.List;
import java.util.Objects;

/**
 * 土方生成用的画布尺度：将「方块数」参数转换为画布距离。
 * <p>
 * 生成入口应通过 {@link #capture(ICoordinateService, List)} 冻结投影，
 * 全程复用同一实例。
 */
public final class EarthworkCanvasScale {
    private final ICoordinateService coordinates;

    private EarthworkCanvasScale(ICoordinateService coordinates) {
        this.coordinates = coordinates;
    }

    public static EarthworkCanvasScale capture(ICoordinateService coordinateService, List<Vec2d> referencePoints) {
        Objects.requireNonNull(coordinateService, "coordinateService");
        return PluginProjectionContext.tryCapture(coordinateService)
            .map(ctx -> new EarthworkCanvasScale(ctx.coordinates()))
            .orElse(new EarthworkCanvasScale(coordinateService));
    }

    /** 1 block = 1 canvas unit（测试或投影不可用时）。 */
    public static EarthworkCanvasScale identity() {
        return new EarthworkCanvasScale(null);
    }

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

    /** 沿线段采样步数：按世界方块长度取 ceil，至少 1 步。 */
    public int stepsAlongSegment(Vec2d start, Vec2d end) {
        if (start == null || end == null) {
            return 1;
        }
        Vec2d segment = end.subtract(start);
        double canvasLength = start.distance(end);
        if (canvasLength <= 1e-9) {
            return 1;
        }
        Vec2d midpoint = start.lerp(end, 0.5);
        double worldLength = canvasToBlocks(canvasLength, midpoint, segment);
        return Math.max(1, (int) Math.ceil(worldLength));
    }

    public double uniformBlocksToCanvas(double blocks, List<Vec2d> polygon) {
        if (blocks == 0.0) {
            return 0.0;
        }
        if (coordinates == null || polygon == null || polygon.size() < 3) {
            return blocks;
        }
        Vec2d probe = centroid(polygon);
        double sum = 0.0;
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            Vec2d start = polygon.get(i);
            Vec2d end = polygon.get((i + 1) % n);
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
