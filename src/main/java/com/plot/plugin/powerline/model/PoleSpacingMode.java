package com.plot.plugin.powerline.model;

/** 杆塔沿线路的布置策略。 */
public enum PoleSpacingMode {
    /** 按固定最大档距自动插杆（默认）。 */
    AUTO_SPACING,
    /** 指定杆塔总数，沿路径等距分布。 */
    TOWER_COUNT,
    /** 仅路径起点与终点。 */
    ENDPOINTS_ONLY,
    /** 起点、终点及超过转角阈值的折点，不插中间杆。 */
    ENDPOINTS_WITH_CORNERS
}
