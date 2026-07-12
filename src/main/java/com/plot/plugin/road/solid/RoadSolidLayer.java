package com.plot.plugin.road.solid;

/**
 * 道路实体层类型（与 Minecraft 方块材质解耦，落地时映射为 block id）。
 */
public enum RoadSolidLayer {
    ROAD,
    SIDEWALK,
    MARKING,
    BIKE_LANE,
    SHOULDER,
    MEDIAN,
    DRAIN,
    BRIDGE,
    TUNNEL,
    STREETLIGHT
}
