package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;

import java.util.List;

/** 画布交叉点叠加层条目（可选含选中路口的圆角预览轮廓）。 */
public record RoadJunctionOverlayEntry(
        IntersectionOverlaySource source,
        String sourceId,
        Vec2d position,
        RoadJunctionOverlayKind kind,
        boolean selected,
        List<Vec2d> filletOutline,
        List<Vec2d> sharpOutline) {

    public RoadJunctionOverlayEntry {
        filletOutline = filletOutline != null ? List.copyOf(filletOutline) : List.of();
        sharpOutline = sharpOutline != null ? List.copyOf(sharpOutline) : List.of();
    }

    public RoadJunctionOverlayEntry(
            IntersectionOverlaySource source,
            String sourceId,
            Vec2d position,
            RoadJunctionOverlayKind kind,
            boolean selected) {
        this(source, sourceId, position, kind, selected, List.of(), List.of());
    }

    /** @deprecated 使用 {@link #sourceId()} */
    @Deprecated
    public String nodeId() {
        return sourceId;
    }
}
