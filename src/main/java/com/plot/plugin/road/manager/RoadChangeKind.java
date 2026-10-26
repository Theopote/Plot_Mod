package com.plot.plugin.road.manager;

/** 路网变更类型：决定预览失效范围。 */
public enum RoadChangeKind {
    /** 默认：完整预览与纵断面采样一并失效。 */
    GENERAL,
    /** 中心线/桩号几何：全量预览失效 + EDITED 签名校验。 */
    GEOMETRY,
    /** 横断面/宽度/材质：保留纵断面采样，仅建造预览过期。 */
    CROSS_SECTION,
    /** 纵断面 PVI / 竖曲线：保留地形采样，仅标记建造预览过期。 */
    VERTICAL_PROFILE,
    /** 交叉口高程关系：同 {@link #VERTICAL_PROFILE}。 */
    JUNCTION,
    /** 求解参数（如分段最大坡度）：全量预览失效。 */
    GENERATION_SETTINGS,
    /** Undo / redo：标记预览过期，但不修改 live 网上的设计权威数据。 */
    HISTORY;

    public boolean preservesProfileSampling() {
        return this == CROSS_SECTION
                || this == VERTICAL_PROFILE
                || this == JUNCTION
                || this == HISTORY;
    }
}
