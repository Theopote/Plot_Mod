package com.plot.plugin.road.profile;

/** 纵断面图渲染密度档位。 */
public enum ProfileChartRenderMode {
    /** Generate 页非当前 Road 缩略图。 */
    MINI,
    /** Generate 页当前 Road 概览。 */
    OVERVIEW,
    /** 独立编辑器窗口。 */
    EDITOR;

    public int maxStationTicks() {
        return switch (this) {
            case MINI -> 3;
            case OVERVIEW, EDITOR -> 5;
        };
    }

    public int maxElevationTicks() {
        return switch (this) {
            case MINI -> 0;
            case OVERVIEW -> 3;
            case EDITOR -> Integer.MAX_VALUE;
        };
    }

    public boolean showElevationAxisLabels() {
        return this == EDITOR;
    }

    public boolean showStationAxisLabels() {
        return this == EDITOR;
    }

    public boolean useCompactLayout() {
        return this == MINI || this == OVERVIEW;
    }

    /**
     * 是否用求解器 design 采样画绿色设计线。
     * <p>
     * 绿色设计线仅表示玩家在编辑器中的纵断面意图，由
     * {@link com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay} 绘制；
     * Generate 概览（MINI/OVERVIEW）只显示地形 + 建造台阶。
     */
    public boolean showDesignProfileLine() {
        return false;
    }

    /**
     * 地形趋势 / 自动引导线（亮蓝或橙色虚线）。编辑器只编辑 VA 绿线，不叠加求解器引导。
     */
    public boolean showGuideLine() {
        return false;
    }

    /**
     * 方块铺设 / 预览拟合道路线。
     * 生成页缩略图与编辑器均显示最近一次预览的建造标高，便于对照设计线；
     * 编辑器在「更新方块线」后改用体素台阶采样。
     */
    public boolean showBuildProfileLine() {
        return true;
    }

    public boolean showIntersectionLabels() {
        return this == EDITOR;
    }

    public boolean showIntersectionConnectors() {
        return this != MINI;
    }

    public float groundLineWidth() {
        return switch (this) {
            case MINI -> 1.4f;
            case OVERVIEW -> 2.0f;
            case EDITOR -> 1.6f;
        };
    }

    public float roadLineWidth() {
        return this == EDITOR ? 2.0f : 2.4f;
    }

    /** Design (intent) line thickness; editor keeps it thinner than the build stair line. */
    public float designLineWidth() {
        return this == EDITOR ? 1.8f : 2.2f;
    }

    public float guideLineWidth() {
        return this == EDITOR ? 1.2f : 1.6f;
    }
}
