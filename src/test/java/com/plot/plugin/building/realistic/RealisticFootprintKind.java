package com.plot.plugin.building.realistic;

/**
 * 真实生成链场景分类（包 A：Footprint → World 审计矩阵）。
 */
public enum RealisticFootprintKind {
    RECT_GRID,
    L_SHAPE,
    CONCAVE,
    ELLIPSE,
    CIRCLE,
    NARROW_INNER_OFFSET,
    THICK_WALL_SMALL,
    OVERLAP_DUPLICATE,
    PITCHED_COMPLEX,
    INVALID
}
