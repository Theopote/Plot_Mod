package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;

/** 画布交叉点叠加层条目。 */
public record RoadJunctionOverlayEntry(
        IntersectionOverlaySource source,
        String sourceId,
        Vec2d position,
        RoadJunctionOverlayKind kind,
        boolean selected) {

    /** @deprecated 使用 {@link #sourceId()} */
    @Deprecated
    public String nodeId() {
        return sourceId;
    }
}
