package com.plot.plugin.pick;

import com.plot.core.model.Shape;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 路径/轮廓拾取会话共用的 selection delta 合并逻辑。
 * <p>
 * 普通点击：只 union 本次新增的选择；点空白清空累积；Ctrl 点击：只对 added/removed delta toggle。
 */
public final class PathPickSelectionMerge {

    public static final class Delta {
        private final List<Shape> added;
        private final List<Shape> removed;

        private Delta(List<Shape> added, List<Shape> removed) {
            this.added = added;
            this.removed = removed;
        }

        public List<Shape> added() {
            return added;
        }

        public List<Shape> removed() {
            return removed;
        }

        public boolean isEmpty() {
            return added.isEmpty() && removed.isEmpty();
        }
    }

    private PathPickSelectionMerge() {
    }

    public static Delta between(List<Shape> previousCanvas, List<Shape> currentCanvas) {
        Set<String> previousIds = shapeIds(previousCanvas);
        Set<String> currentIds = shapeIds(currentCanvas);

        List<Shape> added = new ArrayList<>();
        for (Shape shape : currentCanvas) {
            if (!previousIds.contains(shape.getId())) {
                added.add(shape);
            }
        }

        List<Shape> removed = new ArrayList<>();
        for (Shape shape : previousCanvas) {
            if (!currentIds.contains(shape.getId())) {
                removed.add(shape);
            }
        }

        return new Delta(added, removed);
    }

    public static void merge(
            Map<String, Shape> accumulated,
            List<Shape> previousCanvas,
            List<Shape> currentCanvas,
            boolean ctrlToggle,
            Function<List<Shape>, List<Shape>> findAdoptable) {
        Delta delta = between(previousCanvas, currentCanvas);
        if (delta.isEmpty()) {
            return;
        }

        if (ctrlToggle) {
            toggleAdoptable(accumulated, findAdoptable.apply(delta.added()));
            toggleAdoptable(accumulated, findAdoptable.apply(delta.removed()));
            return;
        }

        if (currentCanvas.isEmpty()) {
            accumulated.clear();
            return;
        }

        unionAdoptable(accumulated, findAdoptable.apply(delta.added()));
    }

    private static void unionAdoptable(Map<String, Shape> accumulated, List<Shape> shapes) {
        for (Shape shape : shapes) {
            accumulated.put(shape.getId(), shape);
        }
    }

    private static void toggleAdoptable(Map<String, Shape> accumulated, List<Shape> shapes) {
        for (Shape shape : shapes) {
            if (accumulated.containsKey(shape.getId())) {
                accumulated.remove(shape.getId());
            } else {
                accumulated.put(shape.getId(), shape);
            }
        }
    }

    private static Set<String> shapeIds(List<Shape> shapes) {
        Set<String> ids = new HashSet<>();
        for (Shape shape : shapes) {
            ids.add(shape.getId());
        }
        return ids;
    }
}
