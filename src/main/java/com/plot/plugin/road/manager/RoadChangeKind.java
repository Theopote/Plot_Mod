package com.plot.plugin.road.manager;

/** 路网变更类型：决定预览失效范围。 */
public enum RoadChangeKind {
    /** 默认：完整预览与纵断面采样一并失效。 */
    GENERAL,
    GEOMETRY,
    CROSS_SECTION,
    /** 纵断面 PVI / 竖曲线：保留地形采样，仅标记建造预览过期。 */
    VERTICAL_PROFILE,
    /** 交叉口高程关系：同 {@link #VERTICAL_PROFILE}。 */
    JUNCTION,
    GENERATION_SETTINGS;

    public boolean preservesProfileSampling() {
        return this == VERTICAL_PROFILE || this == JUNCTION;
    }
}
