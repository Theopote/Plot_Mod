package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.model.Shape;

import java.util.ArrayList;
import java.util.List;

/**
 * 电力线路认领路径校验与提取（仅直线/折线）。
 */
public final class PowerLinePathUtils {

    public static final String REJECT_CURVE_KEY = "plugin.powerline.adopt_reject_curve";

    private PowerLinePathUtils() {
    }

    public static boolean isAdoptableLine(Shape shape) {
        if (shape instanceof LineShape) {
            return shape.getPoints() != null && shape.getPoints().size() >= 2;
        }
        if (shape instanceof PolylineShape polyline) {
            return !polyline.isClosed()
                && polyline.getPoints() != null
                && polyline.getPoints().size() >= 2;
        }
        return false;
    }

    public static boolean isRejectedCurve(Shape shape) {
        return shape instanceof BezierCurveShape;
    }

    public static List<Vec2d> extractPathPoints(Shape shape) {
        if (!isAdoptableLine(shape)) {
            throw new IllegalArgumentException("Shape is not an adoptable power line path");
        }
        List<Vec2d> source = shape.getPoints();
        List<Vec2d> points = new ArrayList<>(source.size());
        for (Vec2d point : source) {
            points.add(point.copy());
        }
        return points;
    }

    public static PowerLinePathSelectionAnalysis analyzeSelection(List<Shape> shapes) {
        return PowerLinePathSelectionAnalysis.analyze(shapes);
    }

    public static List<Shape> findAdoptableLines(List<Shape> shapes) {
        return analyzeSelection(shapes).adoptable();
    }
}
