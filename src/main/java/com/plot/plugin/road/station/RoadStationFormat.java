package com.plot.plugin.road.station;

/**
 * 桩号显示格式。
 */
public enum RoadStationFormat {
    /** 产品 UI 默认：沿道路的距离（米）。 */
    DISTANCE_METERS,
    /** 工程常用：K0+020.0 */
    KILOMETER_PLUS,
    /** 无 K 前缀：0+020.0 */
    PLAIN_PLUS
}
