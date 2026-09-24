package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;

import java.util.List;

/** 单条道路走廊叠加层条目（二维编辑态）。 */
public record RoadOverlayEntry(
        String roadId,
        String displayName,
        List<Vec2d> corridorPoints,
        List<Vec2d> centerlinePoints,
        RoadOverlayState state) {
}
