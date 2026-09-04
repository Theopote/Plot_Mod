package com.plot.plugin.building.model.spec;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.BuildingGeometryUtils;

/**
 * 平面四向（Canvas：+X 东，+Y→世界 +Z 南）。
 * 用于 Facade 边在不同 FloorPlate 拓扑之间的方向继承。
 */
public enum CardinalDirection {
    EAST,
    SOUTH,
    WEST,
    NORTH;

    public static CardinalDirection fromOutwardNormal(Vec2d outward) {
        if (outward == null || outward.length() < 1e-9) {
            return SOUTH;
        }
        Vec2d n = outward.normalize();
        if (Math.abs(n.x) >= Math.abs(n.y)) {
            return n.x >= 0 ? EAST : WEST;
        }
        return n.y >= 0 ? SOUTH : NORTH;
    }
}
