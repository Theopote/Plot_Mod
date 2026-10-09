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

    /**
     * 同一层、同一世界格、同一标高只保留先写入的图元。
     * 用四舍五入后的画布格，与 {@link RoadVoxelRasterizer#toBlockPos} 在 1:1 变换下一致。
     */
    public String dedupKey() {
        return layer.name()
            + '@' + (int) Math.round(planPoint.x)
            + ',' + (int) Math.round(planPoint.y)
            + ',' + elevation;
    }
}
