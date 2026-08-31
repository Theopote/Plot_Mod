package com.plot.plugin.road.alignment;

/**
 * 道路平面几何的权威来源。
 * <p>
 * 有 {@link RoadHorizontalAlignment} 且道路可桩号化时，生成与 profile 通过
 * {@link RoadPlanGeometry} 读取设计线形；生成前由 {@link DerivedCenterlineSynchronizer}
 * 写回 edge 折线缓存。无设计线形时回退 {@link com.plot.plugin.road.model.RoadEdge}
 * 折线中心线。
 */
public enum RoadGeometryAuthority {

    /**
     * 实例折线：{@link com.plot.plugin.road.model.RoadEdge#getCenterlinePoints()}。
     * 无设计平面线形，或道路不可桩号化时的 fallback。
     */
    INSTANCE_CENTERLINE,

    /**
     * 设计线形：{@link RoadHorizontalAlignment} 线元链。
     * 有 alignment 时由 {@link RoadPlanGeometry} 驱动生成采样。
     */
    DESIGN_HORIZONTAL_ALIGNMENT
}
