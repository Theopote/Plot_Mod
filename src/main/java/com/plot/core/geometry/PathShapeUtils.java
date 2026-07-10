package com.plot.core.geometry;

import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.model.Shape;

import java.util.List;

/**
 * 可采纳为道路中心线的路径图形判定
 */
public final class PathShapeUtils {
    private PathShapeUtils() {
    }

    public static boolean isAdoptablePath(Shape shape) {
        return shape instanceof PolylineShape
            || shape instanceof FreeDrawPath
            || shape instanceof BezierCurveShape
            || shape instanceof LineShape;
    }

    public static Shape findFirstAdoptablePath(List<Shape> shapes) {
        if (shapes == null) {
            return null;
        }
        for (Shape shape : shapes) {
            if (isAdoptablePath(shape)) {
                return shape;
            }
        }
        return null;
    }
}
