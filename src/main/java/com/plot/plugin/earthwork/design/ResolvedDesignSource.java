package com.plot.plugin.earthwork.design;

/**
 * 已解析设计面的来源语义（不是 JSON 字段的简单镜像）。
 * <p>
 * Solver / 合成层据此区分「建筑控制标高」「派生基坑」「可拟合景观」等，
 * 避免把所有 design cells 当成同质可调变量。
 */
public enum ResolvedDesignSource {
    /** 建筑 ±0.000 / 基础底。 */
    BUILDING_BASE_ELEVATION,
    /** 由建筑基准推导的基坑坑底。 */
    DERIVED_BUILDING_PIT,
    /** 手动恒定标高。 */
    MANUAL_CONSTANT,
    /** 手动坑底。 */
    MANUAL_PIT_BOTTOM,
    /** 烘焙高程网格（道路等）。 */
    BAKED_ELEVATION,
    /** 贴合现状 + 竖向偏移。 */
    MATCH_EXISTING,
    /** 水平垫层。 */
    LEVEL_PAD,
    /** 单坡平面。 */
    SINGLE_SLOPE,
    /** 三点平面。 */
    THREE_POINT,
    /** 最小二乘 / 最佳拟合。 */
    BEST_FIT,
    /** 多坡面子面。 */
    MULTI_PLANE,
    /** 排水面（当前委托拟合）。 */
    DRAINAGE,
    /** 道路走廊。 */
    ROAD_CORRIDOR,
    UNKNOWN
}
