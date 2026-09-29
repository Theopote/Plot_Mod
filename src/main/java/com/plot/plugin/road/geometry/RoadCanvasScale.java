package com.plot.plugin.road.geometry;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PluginProjectionContext;
import com.plot.core.geometry.WorldProjectionMath;

import java.util.List;
import java.util.Objects;

/**
 * 道路画布叠加层用的尺度换算：将「方块数」转换为画布距离。
 */
public final class RoadCanvasScale {
    private final ICoordinateService coordinates;

    private RoadCanvasScale(ICoordinateService coordinates) {
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates");
    }

    public static RoadCanvasScale capture(ICoordinateService coordinateService, List<Vec2d> referencePoints) {
        Objects.requireNonNull(coordinateService, "coordinateService");
        return new RoadCanvasScale(PluginProjectionContext.capture(coordinateService).coordinates());
    }

    public double blocksToCanvas(double blocks, Vec2d origin, Vec2d direction) {
        if (blocks == 0.0) {
            return 0.0;
        }
        return blocks * WorldProjectionMath.canvasUnitsPerWorldBlock(coordinates, origin, direction);
    }

    public double uniformBlocksToCanvas(double blocks, List<Vec2d> centerline) {
        if (blocks == 0.0) {
            return 0.0;
        }
        if (centerline == null || centerline.size() < 2) {
            return blocks;
        }
        Vec2d probe = centroid(centerline);
        double sum = 0.0;
        int count = centerline.size() - 1;
        for (int i = 0; i < count; i++) {
            Vec2d start = centerline.get(i);
            Vec2d end = centerline.get(i + 1);
            sum += WorldProjectionMath.canvasUnitsPerWorldBlock(coordinates, probe, end.subtract(start));
        }
        return blocks * (sum / count);
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
