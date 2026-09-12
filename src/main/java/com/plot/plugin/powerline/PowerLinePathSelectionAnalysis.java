package com.plot.plugin.powerline;

import com.plot.core.model.Shape;
import com.plot.plugin.powerline.path.PowerLinePathAdapters;

import java.util.ArrayList;
import java.util.List;

/**
 * 画布当前选中图形的认领分类结果。
 */
public record PowerLinePathSelectionAnalysis(
        List<Shape> adoptable,
        List<Shape> rejectedCurves,
        List<Shape> unsupported) {

    public static final PowerLinePathSelectionAnalysis EMPTY =
        new PowerLinePathSelectionAnalysis(List.of(), List.of(), List.of());

    public PowerLinePathSelectionAnalysis {
        adoptable = List.copyOf(adoptable);
        rejectedCurves = List.copyOf(rejectedCurves);
        unsupported = List.copyOf(unsupported);
    }

    public static PowerLinePathSelectionAnalysis analyze(List<Shape> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return EMPTY;
        }
        List<Shape> adoptable = new ArrayList<>();
        List<Shape> rejectedCurves = new ArrayList<>();
        List<Shape> unsupported = new ArrayList<>();
        for (Shape shape : shapes) {
            if (PowerLinePathAdapters.isAdoptable(shape)) {
                adoptable.add(shape);
            } else if (PowerLinePathUtils.isRejectedCurve(shape)) {
                rejectedCurves.add(shape);
            } else {
                unsupported.add(shape);
            }
        }
        return new PowerLinePathSelectionAnalysis(adoptable, rejectedCurves, unsupported);
    }

    public boolean hasCanvasSelection() {
        return !adoptable.isEmpty() || !rejectedCurves.isEmpty() || !unsupported.isEmpty();
    }

    public boolean canAdopt() {
        return !adoptable.isEmpty();
    }

    public int skippedCount() {
        return rejectedCurves.size() + unsupported.size();
    }
}
