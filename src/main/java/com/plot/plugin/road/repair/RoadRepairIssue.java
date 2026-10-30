package com.plot.plugin.road.repair;

/**
 * 单条道路自动修复可检测/可处理的问题类型。
 */
public enum RoadRepairIssue {
    INTERSECTION_INCOMPLETE,
    INTERSECTION_PENDING,
    TOPOLOGY_DISCONNECTED,
    TOPOLOGY_BRANCHING,
    TOPOLOGY_CYCLE,
    SEGMENT_ORDER_MISMATCH,
    HORIZONTAL_ALIGNMENT_MISMATCH,
    JUNCTION_ENDPOINT_MISMATCH,
    VERTICAL_PROFILE_MISMATCH,
    CENTERLINE_SELF_INTERSECTION,
    CENTERLINE_SELF_OVERLAP,
    CENTERLINE_NON_LINEAR,
    STEEP_GRADE;

    public String titleKey() {
        return "plugin.road.fix_road.issue." + name().toLowerCase();
    }

    public String detailKey() {
        return titleKey() + ".detail";
    }

    /**
     * 一键自动修复流水线是否会尝试处理该问题。
     * 中心线自交/自重叠需要手动改折线，无法自动消除。
     */
    public boolean isAutoFixable() {
        return this != CENTERLINE_SELF_INTERSECTION && this != CENTERLINE_SELF_OVERLAP;
    }
}
