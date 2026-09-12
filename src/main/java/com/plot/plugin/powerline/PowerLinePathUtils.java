package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.model.Shape;
import com.plot.plugin.powerline.path.PowerLinePathAdapters;

import java.util.ArrayList;
import java.util.List;

/**
 * 电力线路认领路径校验与提取。
 */
public final class PowerLinePathUtils {

    public static final String REJECT_CURVE_KEY = "plugin.powerline.adopt_reject_curve";
    public static final String REJECT_CLOSED_LOOP_KEY = "plugin.powerline.adopt_reject_closed_loop";

    private PowerLinePathUtils() {
    }

    public static boolean isAdoptableLine(Shape shape) {
        return PowerLinePathAdapters.isAdoptable(shape);
    }

    public static boolean isRejectedCurve(Shape shape) {
        return shape instanceof BezierCurveShape && !PowerLinePathAdapters.isAdoptable(shape);
    }

    /**
     * @deprecated 认领请使用 {@link com.plot.plugin.powerline.path.PowerLinePathLayout#adopt}。
     */
    @Deprecated
    public static List<Vec2d> extractPathPoints(Shape shape) {
        if (shape instanceof LineShape) {
            List<Vec2d> source = shape.getPoints();
            List<Vec2d> points = new ArrayList<>(source.size());
            for (Vec2d point : source) {
                points.add(point.copy());
            }
            return points;
        }
        if (shape instanceof PolylineShape polyline) {
            if (polyline.isClosed()) {
                throw new IllegalArgumentException("Shape is not an adoptable power line path");
            }
            List<Vec2d> source = polyline.getPoints();
            List<Vec2d> points = new ArrayList<>(source.size());
            for (Vec2d point : source) {
                points.add(point.copy());
            }
            return points;
        }
        throw new IllegalArgumentException("Shape is not an adoptable power line path");
    }

    public static PowerLinePathSelectionAnalysis analyzeSelection(List<Shape> shapes) {
        return PowerLinePathSelectionAnalysis.analyze(shapes);
    }

    public static List<Shape> findAdoptableLines(List<Shape> shapes) {
        return analyzeSelection(shapes).adoptable();
    }
}
