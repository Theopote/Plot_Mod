package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.dto.ModifyParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrimHandlerTest {

    private TrimHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TrimHandler(AppState.getInstance());
    }

    @Test
    void validateModificationRequiresTrimPointAndBoundary() {
        IModifyHandler.ValidationResult empty = handler.validateModification(
                List.of(new LineShape(new Vec2d(0, 0), new Vec2d(10, 0))),
                new ModifyParameters());
        assertFalse(empty.isValid());

        ModifyParameters missingBoundary = new ModifyParameters();
        missingBoundary.setParameter("trimPoint", new Vec2d(5, 0));
        assertFalse(handler.validateModification(
                List.of(new LineShape(new Vec2d(0, 0), new Vec2d(10, 0))),
                missingBoundary).isValid());
    }

    @Test
    void validateModificationRequiresFencePointsInFenceMode() {
        ModifyParameters params = new ModifyParameters();
        params.setParameter("fenceMode", true);
        params.setParameter("fencePoints", List.of(new Vec2d(0, 0), new Vec2d(10, 0)));

        assertFalse(handler.validateModification(
                List.of(new LineShape(new Vec2d(0, 0), new Vec2d(10, 0))),
                params).isValid());
    }

    @Test
    void calculateModifiedShapesTrimsSegmentNearestToClickPoint() {
        LineShape target = new LineShape(new Vec2d(0, 0), new Vec2d(20, 0));
        LineShape boundary = new LineShape(new Vec2d(10, -10), new Vec2d(10, 10));
        ModifyParameters params = boundaryTrimParams(new Vec2d(5, 0), List.of(boundary));

        List<Shape> result = handler.calculateModifiedShapes(List.of(target), params);

        assertEquals(1, result.size());
        LineShape trimmed = (LineShape) result.getFirst();
        assertEquals(10.0, trimmed.getStart().x, 1e-3);
        assertEquals(20.0, trimmed.getEnd().x, 1e-3);
    }

    @Test
    void calculateModifiedShapesFenceModeKeepsOutsideSegments() {
        LineShape target = new LineShape(new Vec2d(5, -5), new Vec2d(5, 15));
        List<Vec2d> fence = List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                new Vec2d(10, 10),
                new Vec2d(0, 10));
        ModifyParameters params = fenceTrimParams(fence);

        List<Shape> result = handler.calculateModifiedShapes(List.of(target), params);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(shape -> shape instanceof LineShape));
    }

    private static ModifyParameters boundaryTrimParams(Vec2d trimPoint, List<Shape> boundaries) {
        ModifyParameters params = new ModifyParameters();
        params.setParameter("trimPoint", trimPoint);
        params.setParameter("boundaryShapes", boundaries);
        params.setParameter("fenceMode", false);
        return params;
    }

    private static ModifyParameters fenceTrimParams(List<Vec2d> fencePoints) {
        ModifyParameters params = new ModifyParameters();
        params.setParameter("fenceMode", true);
        params.setParameter("fencePoints", fencePoints);
        return params;
    }
}
