package com.plot.plugin.road.validation;

/**
 * 校验问题建议的用户操作。UI 按钮文案见 {@code plugin.road.issue.action.*}。
 */
public enum RoadValidationAction {
    RECONCILE_INTERSECTIONS,
    SYNC_SEGMENT_ORDER,
    SNAP_TO_JUNCTION,
    MATERIALIZE_ALIGNMENT,
    SMOOTH_GRADE,
    MAKE_SHORT_ROADS_FLAT,
    REPAIR_ROAD_TOPOLOGY
}
