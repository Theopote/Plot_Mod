package com.plot.plugin.road.vertical;

/**
 * 纵断面工程验证违反类型。
 */
public enum VerticalAlignmentViolationKind {
    /** 两个及以上 PVI 共用同一桩号 */
    PVI_STATION_DUPLICATE,
    /** 存储顺序桩号非严格递增（含乱序） */
    PVI_STATION_NOT_INCREASING,
    /** 相邻竖曲线桩号区间重叠 */
    VERTICAL_CURVE_OVERLAP,
    /** 竖曲线超出道路链或纵断面端点桩号范围 */
    VERTICAL_CURVE_OUT_OF_RANGE
}
