package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.ui.tools.impl.modify.dto.ModifyParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreakHandlerTest {

    private BreakHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BreakHandler();
    }

    @Test
    void validateModificationRequiresBreakParameters() {
        IModifyHandler.ValidationResult missingTarget = handler.validateModification(
                List.of(), new ModifyParameters());
        assertFalse(missingTarget.isValid());

        ModifyParameters missingMode = new ModifyParameters();
        missingMode.setParameter("targetShape", new LineShape(new Vec2d(0, 0), new Vec2d(10, 0)));
        missingMode.setParameter("firstBreakPoint", new Vec2d(5, 0));
        assertFalse(handler.validateModification(List.of(), missingMode).isValid());
    }

    @Test
    void validateModificationRequiresSecondPointForTwoPointMode() {
        ModifyParameters params = breakParams(
                new LineShape(new Vec2d(0, 0), new Vec2d(10, 0)),
                new Vec2d(3, 0),
                null,
                "TWO_POINT");

        assertFalse(handler.validateModification(List.of(), params).isValid());
    }

    @Test
    void calculateModifiedShapesSplitsLineAtSinglePoint() {
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(20, 0));
        ModifyParameters params = breakParams(line, new Vec2d(10, 0), null, "SINGLE_POINT");

        List<Shape> result = handler.calculateModifiedShapes(List.of(line), params);

        assertEquals(2, result.size());
        LineShape first = (LineShape) result.get(0);
        LineShape second = (LineShape) result.get(1);
        assertEquals(0.0, first.getStart().x, 1e-6);
        assertEquals(10.0, first.getEnd().x, 1e-6);
        assertEquals(10.0, second.getStart().x, 1e-6);
        assertEquals(20.0, second.getEnd().x, 1e-6);
    }

    @Test
    void calculateModifiedShapesRemovesSegmentBetweenTwoPoints() {
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(20, 0));
        ModifyParameters params = breakParams(line, new Vec2d(7, 0), new Vec2d(13, 0), "TWO_POINT");

        List<Shape> result = handler.calculateModifiedShapes(List.of(line), params);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(shape -> {
            LineShape segment = (LineShape) shape;
            return Math.abs(segment.getStart().x) < 1e-6 && Math.abs(segment.getEnd().x - 7.0) < 1e-6;
        }));
        assertTrue(result.stream().anyMatch(shape -> {
            LineShape segment = (LineShape) shape;
            return Math.abs(segment.getStart().x - 13.0) < 1e-6 && Math.abs(segment.getEnd().x - 20.0) < 1e-6;
        }));
    }

    private static ModifyParameters breakParams(
            Shape target, Vec2d first, Vec2d second, String mode) {
        ModifyParameters params = new ModifyParameters();
        params.setParameter("targetShape", target);
        params.setParameter("firstBreakPoint", first);
        params.setParameter("breakMode", mode);
        if (second != null) {
            params.setParameter("secondBreakPoint", second);
        }
        return params;
    }
}
