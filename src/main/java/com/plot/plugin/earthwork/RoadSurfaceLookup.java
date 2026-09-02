package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;

/**
 * 运行时查询道路设计纵断面标高（由道路插件提供）。
 */
@FunctionalInterface
public interface RoadSurfaceLookup {
    RoadSurfaceLookup NONE = (edgeId, planPoint) -> null;

    /**
     * @return 设计路面标高（方块 Y），无法采样时返回 null
     */
    Integer sampleDesignY(String roadEdgeId, Vec2d planPoint);
}
