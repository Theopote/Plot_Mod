package com.plot.plugin.road.centerline;

/**
 * 中心线编辑操作结果。
 */
public enum CenterlineEditStatus {
    SUCCESS,
    EDGE_NOT_FOUND,
    ROAD_NOT_FOUND,
    NODE_NOT_FOUND,
    INVALID_DISTANCE,
    INVALID_VERTEX,
    INVALID_RADIUS,
    SPLIT_FAILED,
    MERGE_FAILED,
    TOO_FEW_POINTS,
    ALIGNMENT_STATIONS_INVALID
}
