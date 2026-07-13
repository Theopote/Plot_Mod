package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.dto.ExtendParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtendHandlerTest {

    private ExtendHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ExtendHandler(AppState.getInstance());
    }

    @Test
    void validateModificationRequiresExtendParameters() {
        IModifyHandler.ValidationResult wrongType = handler.validateModification(
                List.of(new LineShape(new Vec2d(0, 0), new Vec2d(5, 0))),
                new com.plot.ui.tools.impl.modify.dto.ModifyParameters());
        assertFalse(wrongType.isValid());

        ExtendParameters valid = extendParams(
                new Vec2d(5, 0),
                List.of(new LineShape(new Vec2d(10, -10), new Vec2d(10, 10))));
        assertTrue(handler.validateModification(
                List.of(new LineShape(new Vec2d(0, 0), new Vec2d(5, 0))),
                valid).isValid());
    }

    @Test
    void calculateModifiedShapesExtendsLineToBoundary() {
        LineShape source = new LineShape(new Vec2d(0, 0), new Vec2d(5, 0));
        LineShape boundary = new LineShape(new Vec2d(10, -10), new Vec2d(10, 10));
        ExtendParameters params = extendParams(new Vec2d(5, 0), List.of(boundary));

        List<Shape> result = handler.calculateModifiedShapes(List.of(source), params);

        LineShape extended = (LineShape) result.getFirst();
        assertEquals(0.0, extended.getStart().x, 1e-6);
        assertEquals(0.0, extended.getStart().y, 1e-6);
        assertEquals(10.0, extended.getEnd().x, 1e-3);
        assertEquals(0.0, extended.getEnd().y, 1e-3);
    }

    @Test
    void calculateModifiedShapesKeepsOriginalWhenExtendPointIsNotNearEndpoint() {
        LineShape source = new LineShape(new Vec2d(0, 0), new Vec2d(5, 0));
        LineShape boundary = new LineShape(new Vec2d(10, -10), new Vec2d(10, 10));
        ExtendParameters params = extendParams(new Vec2d(2, 0), List.of(boundary));

        List<Shape> result = handler.calculateModifiedShapes(List.of(source), params);

        assertEquals(source, result.getFirst());
    }

    private static ExtendParameters extendParams(Vec2d extendPoint, List<Shape> boundaries) {
        return ExtendParameters.of(extendPoint, boundaries, 1.0, 5.0);
    }
}
