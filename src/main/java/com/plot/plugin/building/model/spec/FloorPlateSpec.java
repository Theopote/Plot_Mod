package com.plot.plugin.building.model.spec;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.polygon.PolygonOffset;

import java.util.ArrayList;
import java.util.List;

/**
 * 楼层轮廓板：指定一段楼层范围内使用的平面 footprint。
 * <p>
 * floorStart / floorEnd 均为<strong>含端点</strong>的楼层索引（0 = 首层）。
 */
public final class FloorPlateSpec {
    private final int floorStart;
    private final int floorEnd;
    private final List<Vec2d> outerPoints;

    public FloorPlateSpec(int floorStart, int floorEnd, List<Vec2d> outerPoints) {
        if (outerPoints == null || outerPoints.size() < 3) {
            throw new IllegalArgumentException("floor plate requires at least 3 outer points");
        }
        if (floorStart < 0 || floorEnd < floorStart) {
            throw new IllegalArgumentException("invalid floor range");
        }
        this.floorStart = floorStart;
        this.floorEnd = floorEnd;
        this.outerPoints = copyPoints(outerPoints);
    }

    public static FloorPlateSpec of(int floorStart, int floorEnd, List<Vec2d> outerPoints) {
        return new FloorPlateSpec(floorStart, floorEnd, outerPoints);
    }

    /**
     * 从基础 footprint 均匀内缩，用于退台/塔楼等体量变化。
     */
    public static FloorPlateSpec insetFrom(
            int floorStart,
            int floorEnd,
            List<Vec2d> baseFootprint,
            double insetDistance) {
        PolygonOffset.OffsetResult result = PolygonOffset.offsetInward(baseFootprint, insetDistance);
        if (!result.success() || result.points().size() < 3) {
            throw new IllegalArgumentException("inset produced invalid floor plate");
        }
        return new FloorPlateSpec(floorStart, floorEnd, result.points());
    }

    public int floorStart() {
        return floorStart;
    }

    public int floorEnd() {
        return floorEnd;
    }

    public List<Vec2d> outerPoints() {
        return copyPoints(outerPoints);
    }

    public boolean coversFloor(int floorIndex) {
        return floorIndex >= floorStart && floorIndex <= floorEnd;
    }

    private static List<Vec2d> copyPoints(List<Vec2d> points) {
        List<Vec2d> copy = new ArrayList<>(points.size());
        for (Vec2d point : points) {
            copy.add(point != null ? point.copy() : new Vec2d(0, 0));
        }
        return List.copyOf(copy);
    }
}
