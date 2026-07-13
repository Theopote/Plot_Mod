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

class MirrorHandlerTest {

    private MirrorHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MirrorHandler(AppState.getInstance());
    }

    @Test
    void mirrorPointReflectsAcrossVerticalAxis() {
        Vec2d mirrored = handler.mirrorPoint(
                new Vec2d(5, 3),
                new Vec2d(0, 0),
                new Vec2d(0, 10));

        assertEquals(-5.0, mirrored.x, 1e-6);
        assertEquals(3.0, mirrored.y, 1e-6);
    }

    @Test
    void mirrorPointReflectsAcrossHorizontalAxis() {
        Vec2d mirrored = handler.mirrorPoint(
                new Vec2d(4, 6),
                new Vec2d(0, 0),
                new Vec2d(10, 0));

        assertEquals(4.0, mirrored.x, 1e-6);
        assertEquals(-6.0, mirrored.y, 1e-6);
    }

    @Test
    void isOrthogonalAxisDetectsAxisAlignedLines() {
        assertTrue(handler.isOrthogonalAxis(new Vec2d(0, 0), new Vec2d(10, 0)));
        assertTrue(handler.isOrthogonalAxis(new Vec2d(0, 0), new Vec2d(0, 10)));
        assertFalse(handler.isOrthogonalAxis(new Vec2d(0, 0), new Vec2d(10, 10)));
    }

    @Test
    void calculateModifiedShapesMirrorsLineAcrossYAxis() {
        LineShape line = new LineShape(new Vec2d(5, 0), new Vec2d(10, 2));
        ModifyParameters params = new ModifyParameters();
        params.setVec2d(ModifyParameters.MIRROR_AXIS_START, new Vec2d(0, 0));
        params.setVec2d(ModifyParameters.MIRROR_AXIS_END, new Vec2d(0, 10));

        List<Shape> mirrored = handler.calculateModifiedShapes(List.of(line), params);

        assertEquals(1, mirrored.size());
        LineShape result = (LineShape) mirrored.getFirst();
        assertEquals(-5.0, result.getStart().x, 1e-6);
        assertEquals(0.0, result.getStart().y, 1e-6);
        assertEquals(-10.0, result.getEnd().x, 1e-6);
        assertEquals(2.0, result.getEnd().y, 1e-6);
    }

    @Test
    void validateModificationRejectsEmptySelection() {
        ModifyParameters params = new ModifyParameters();
        params.setVec2d(ModifyParameters.MIRROR_AXIS_START, new Vec2d(0, 0));
        params.setVec2d(ModifyParameters.MIRROR_AXIS_END, new Vec2d(0, 10));

        IModifyHandler.ValidationResult result = handler.validateModification(List.of(), params);

        assertFalse(result.isValid());
    }
}
