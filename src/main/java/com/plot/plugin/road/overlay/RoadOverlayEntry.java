package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.geometry.RoadCorridorGeometry;

import java.util.List;

/** 单条道路走廊叠加层条目（二维编辑态）。 */
public record RoadOverlayEntry(
        String roadId,
        String displayName,
        RoadCorridorGeometry corridorGeometry,
        List<Vec2d> centerlinePoints,
        RoadOverlayState state,
        Vec2d profileSeamPosition,
        RoadCorridorGeometry slopeBatterGeometry,
        List<List<Vec2d>> spiralHighlightSegments) {

    public RoadOverlayEntry {
        spiralHighlightSegments = spiralHighlightSegments != null
            ? List.copyOf(spiralHighlightSegments)
            : List.of();
    }

    public RoadOverlayEntry(
            String roadId,
            String displayName,
            RoadCorridorGeometry corridorGeometry,
            List<Vec2d> centerlinePoints,
            RoadOverlayState state) {
        this(roadId, displayName, corridorGeometry, centerlinePoints, state, null, null, List.of());
    }

    public RoadOverlayEntry(
            String roadId,
            String displayName,
            RoadCorridorGeometry corridorGeometry,
            List<Vec2d> centerlinePoints,
            RoadOverlayState state,
            Vec2d profileSeamPosition) {
        this(roadId, displayName, corridorGeometry, centerlinePoints, state, profileSeamPosition, null, List.of());
    }

    public RoadOverlayEntry(
            String roadId,
            String displayName,
            RoadCorridorGeometry corridorGeometry,
            List<Vec2d> centerlinePoints,
            RoadOverlayState state,
            Vec2d profileSeamPosition,
            RoadCorridorGeometry slopeBatterGeometry) {
        this(
            roadId,
            displayName,
            corridorGeometry,
            centerlinePoints,
            state,
            profileSeamPosition,
            slopeBatterGeometry,
            List.of());
    }

    /** 开放道路的单环轮廓；闭环道路为外环（拾取/框选回退用）。 */
    public List<Vec2d> corridorPoints() {
        return corridorGeometry != null ? corridorGeometry.primaryFillContour() : List.of();
    }

    public boolean hasSlopeBatterOverlay() {
        return slopeBatterGeometry != null && slopeBatterGeometry.primaryFillContour().size() >= 3;
    }

    public boolean hasSpiralHighlights() {
        return !spiralHighlightSegments.isEmpty();
    }
}
