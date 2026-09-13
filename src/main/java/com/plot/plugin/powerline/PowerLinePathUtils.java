package com.plot.plugin.powerline;

import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.model.Shape;
import com.plot.plugin.powerline.path.PowerLinePathAdapters;

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

    public static PowerLinePathSelectionAnalysis analyzeSelection(List<Shape> shapes) {
        return PowerLinePathSelectionAnalysis.analyze(shapes);
    }

    public static List<Shape> findAdoptableLines(List<Shape> shapes) {
        return analyzeSelection(shapes).adoptable();
    }
}
