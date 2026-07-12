package com.plot.plugin.road.solid;

import com.plot.api.geometry.Vec2d;

/**
 * 平面坐标 + 标高 + 层类型 + 材质（体素蓝图，不含 BlockPos）。
 */
public record RoadSolidPrimitive(Vec2d planPoint, int elevation, RoadSolidLayer layer, String materialId) {
    public RoadSolidPrimitive(Vec2d planPoint, int elevation, RoadSolidLayer layer) {
        this(planPoint, elevation, layer, null);
    }

    public RoadSolidPrimitive {
        planPoint = planPoint != null ? planPoint : new Vec2d(0, 0);
    }

    public String dedupKey() {
        return layer.name() + '@' + planPoint.x + ',' + planPoint.y + ',' + elevation;
    }
}
