package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;

import java.util.List;

/** 画布交叉点叠加层条目（选中路口可含锐角参考与路缘圆弧预览）。 */
public record RoadJunctionOverlayEntry(
        IntersectionOverlaySource source,
        String sourceId,
        Vec2d position,
        RoadJunctionOverlayKind kind,
        boolean selected,
        List<Vec2d> sharpOutline,
        List<List<Vec2d>> filletArcs) {

    public RoadJunctionOverlayEntry {
        sharpOutline = sharpOutline != null ? List.copyOf(sharpOutline) : List.of();
        filletArcs = filletArcs == null
            ? List.of()
            : filletArcs.stream()
                .map(arc -> arc != null ? List.copyOf(arc) : List.<Vec2d>of())
                .filter(arc -> arc.size() >= 2)
                .toList();
    }

    public RoadJunctionOverlayEntry(
            IntersectionOverlaySource source,
            String sourceId,
            Vec2d position,
            RoadJunctionOverlayKind kind,
            boolean selected) {
        this(source, sourceId, position, kind, selected, List.of(), List.of());
    }

    /** @deprecated 预览已改为 {@link #filletArcs()}；保留空列表以兼容旧调用。 */
    @Deprecated
    public List<Vec2d> filletOutline() {
        return List.of();
    }

    /** @deprecated 使用 {@link #sourceId()} */
    @Deprecated
    public String nodeId() {
        return sourceId;
    }
}
