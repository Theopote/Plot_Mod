package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;

/** 画布交叉点叠加层条目。 */
public record RoadJunctionOverlayEntry(
        String nodeId,
        Vec2d position,
        RoadJunctionOverlayKind kind,
        boolean selected) {
}
