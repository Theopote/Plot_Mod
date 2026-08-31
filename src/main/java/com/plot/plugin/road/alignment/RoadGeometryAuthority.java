package com.plot.plugin.road.alignment;

/**
 * 道路平面几何的权威来源（Phase 2 过渡模型）。
 * <p>
 * <b>当前生成与拓扑仍以 {@link com.plot.plugin.road.model.RoadEdge} 折线中心线为准</b>；
 * {@link RoadHorizontalAlignment} 是设计语义层。两者不一致时，落地以 INSTANCE 为准，
 * 工程检查应报告 DESIGN 偏差（见 {@link HorizontalAlignmentCenterlineConsistency}）。
 */
public enum RoadGeometryAuthority {

    /**
     * 实例几何：{@link com.plot.plugin.road.model.RoadEdge#getCenterlinePoints()}。
     * 生成、求交、横断面采样、纵断面叠加均使用此来源。
     */
    INSTANCE_CENTERLINE,

    /**
     * 设计线形：{@link RoadHorizontalAlignment} 线元链。
     * 编辑展示与工程校验的设计基准；尚未驱动 edge 几何替换。
     */
    DESIGN_HORIZONTAL_ALIGNMENT
}
