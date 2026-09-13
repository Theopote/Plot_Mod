package com.plot.plugin.pick;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathPickSelectionMergeTest {

    @Test
    void normalClickUnionsAddedPathsWithoutRemovingExistingAccumulated() {
        Map<String, Shape> accumulated = new LinkedHashMap<>();
        LineShape pathA = line(0, 0, 10, 0);
        LineShape pathB = line(20, 0, 30, 0);

        merge(accumulated, List.of(), List.of(pathA), false);
        merge(accumulated, List.of(pathA), List.of(pathB), false);

        assertEquals(List.of(pathA.getId(), pathB.getId()), List.copyOf(accumulated.keySet()));
    }

    @Test
    void ctrlClickAddsOnlyDeltaWithoutTogglingExistingAccumulated() {
        Map<String, Shape> accumulated = new LinkedHashMap<>();
        LineShape pathA = line(0, 0, 10, 0);
        LineShape pathB = line(20, 0, 30, 0);

        merge(accumulated, List.of(), List.of(pathA), false);
        merge(accumulated, List.of(pathA), List.of(pathA, pathB), true);

        assertEquals(List.of(pathA.getId(), pathB.getId()), List.copyOf(accumulated.keySet()));
    }

    @Test
    void ctrlClickRemovesDeselectedPathFromAccumulated() {
        Map<String, Shape> accumulated = new LinkedHashMap<>();
        LineShape pathA = line(0, 0, 10, 0);

        merge(accumulated, List.of(), List.of(pathA), false);
        merge(accumulated, List.of(pathA), List.of(), true);

        assertTrue(accumulated.isEmpty());
    }

    private static void merge(
            Map<String, Shape> accumulated,
            List<Shape> previous,
            List<Shape> current,
            boolean ctrlToggle) {
        PathPickSelectionMerge.merge(accumulated, previous, current, ctrlToggle, shapes -> shapes);
    }

    private static LineShape line(double x1, double y1, double x2, double y2) {
        return new LineShape(new Vec2d(x1, y1), new Vec2d(x2, y2));
    }
}
