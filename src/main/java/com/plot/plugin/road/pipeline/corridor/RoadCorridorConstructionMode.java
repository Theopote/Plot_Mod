package com.plot.plugin.road.pipeline.corridor;

/** 列级施工模式，决定清障 envelope 语义。 */
public enum RoadCorridorConstructionMode {
    OPEN,
    BRIDGE,
    TUNNEL,
    /** Tunnel portal mouth: clearance capped to designed cavity, not terrain top. */
    PORTAL
}
