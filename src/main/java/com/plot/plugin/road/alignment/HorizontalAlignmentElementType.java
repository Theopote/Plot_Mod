package com.plot.plugin.road.alignment;

/**
 * 平面线形线元类型。
 */
public enum HorizontalAlignmentElementType {
    /** 直线（切线） */
    TANGENT,
    /** 圆曲线 */
    CIRCULAR_ARC,
    /** 缓和曲线（clothoid，曲率沿长度线性变化） */
    SPIRAL
}
