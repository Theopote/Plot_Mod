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
        RoadCorridorGeometry slopeBatterGeometry) {

    public RoadOverlayEntry(
            String roadId,
            String displayName,
            RoadCorridorGeometry corridorGeometry,
            List<Vec2d> centerlinePoints,
            RoadOverlayState state) {
        this(roadId, displayName, corridorGeometry, centerlinePoints, state, null, null);
    }

    public RoadOverlayEntry(
            String roadId,
            String displayName,
            RoadCorridorGeometry corridorGeometry,
            List<Vec2d> centerlinePoints,
            RoadOverlayState state,
            Vec2d profileSeamPosition) {
        this(roadId, displayName, corridorGeometry, centerlinePoints, state, profileSeamPosition, null);
    }

    /** 开放道路的单环轮廓；闭环道路为外环（拾取/框选回退用）。 */
    public List<Vec2d> corridorPoints() {
        return corridorGeometry != null ? corridorGeometry.primaryFillContour() : List.of();
    }

    public boolean hasSlopeBatterOverlay() {
        return slopeBatterGeometry != null && slopeBatterGeometry.primaryFillContour().size() >= 3;
    }
}
