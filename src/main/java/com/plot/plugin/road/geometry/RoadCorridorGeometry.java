package com.plot.plugin.road.geometry;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.List;

/** 道路二维走廊几何：左右边界与可填充轮廓（开放为单环，闭环为外环+内孔）。 */
public record RoadCorridorGeometry(
        List<Vec2d> centerline,
        List<Vec2d> leftBoundary,
        List<Vec2d> rightBoundary,
        List<List<Vec2d>> fillContours,
        boolean closed,
        List<String> warnings) {

    public RoadCorridorGeometry {
        centerline = copyPoints(centerline);
        leftBoundary = copyPoints(leftBoundary);
        rightBoundary = copyPoints(rightBoundary);
        List<List<Vec2d>> copiedContours = new ArrayList<>();
        if (fillContours != null) {
            for (List<Vec2d> contour : fillContours) {
                copiedContours.add(copyPoints(contour));
            }
        }
        fillContours = List.copyOf(copiedContours);
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }

    /** 开放道路的单环填充轮廓；闭环道路返回外环。 */
    public List<Vec2d> primaryFillContour() {
        return fillContours.isEmpty() ? List.of() : fillContours.getFirst();
    }

    public List<Vec2d> outerContour() {
        return fillContours.isEmpty() ? List.of() : fillContours.getFirst();
    }

    public List<Vec2d> innerHole() {
        return closed && fillContours.size() > 1 ? fillContours.get(1) : List.of();
    }

    private static List<Vec2d> copyPoints(List<Vec2d> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        List<Vec2d> copy = new ArrayList<>(points.size());
        for (Vec2d point : points) {
            if (point != null) {
                copy.add(point.copy());
            }
        }
        return List.copyOf(copy);
    }
}
