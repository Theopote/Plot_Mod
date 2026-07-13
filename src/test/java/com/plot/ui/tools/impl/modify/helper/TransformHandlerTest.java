package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.dto.TransformParams;
import com.plot.ui.tools.impl.modify.enums.TransformMode;
import com.plot.ui.tools.impl.modify.helper.BoundingBoxControlManager.ControlPointType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TransformHandlerTest {

    private TransformHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TransformHandler(AppState.getInstance());
    }

    @Test
    void validateModificationRejectsEmptySelection() {
        IModifyHandler.ValidationResult result = handler.validateModification(
                List.of(),
                new com.plot.ui.tools.impl.modify.dto.ModifyParameters());

        assertFalse(result.isValid());
    }

    @Test
    void calculateModifiedShapesTranslatesWithHorizontalMode() {
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        TransformParams params = TransformParams.builder()
                .dragVector(new Vec2d(5, 9))
                .mode(TransformMode.HORIZONTAL)
                .controlPointType(ControlPointType.TOP_LEFT)
                .controlPointIndex(0)
                .build();

        List<Shape> result = handler.calculateModifiedShapes(List.of(line), params);

        LineShape transformed = (LineShape) result.getFirst();
        assertEquals(5.0, transformed.getStart().x, 1e-6);
        assertEquals(0.0, transformed.getStart().y, 1e-6);
        assertEquals(15.0, transformed.getEnd().x, 1e-6);
        assertEquals(0.0, transformed.getEnd().y, 1e-6);
    }

    @Test
    void calculateModifiedShapesTranslatesWithVerticalMode() {
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        TransformParams params = TransformParams.builder()
                .dragVector(new Vec2d(9, 4))
                .mode(TransformMode.VERTICAL)
                .controlPointType(ControlPointType.TOP_LEFT)
                .controlPointIndex(0)
                .build();

        List<Shape> result = handler.calculateModifiedShapes(List.of(line), params);

        LineShape transformed = (LineShape) result.getFirst();
        assertEquals(0.0, transformed.getStart().x, 1e-6);
        assertEquals(4.0, transformed.getStart().y, 1e-6);
        assertEquals(10.0, transformed.getEnd().x, 1e-6);
        assertEquals(4.0, transformed.getEnd().y, 1e-6);
    }

    @Test
    void createModifyCommandBuildsTransformCommand() {
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        TransformParams params = TransformParams.builder()
                .dragVector(new Vec2d(3, 0))
                .mode(TransformMode.HORIZONTAL)
                .controlPointType(ControlPointType.TOP_LEFT)
                .controlPointIndex(0)
                .build();
        List<Shape> preview = handler.calculateModifiedShapes(List.of(line), params);

        ModifyCommand command = handler.createModifyCommand(List.of(line), preview, params);

        assertNotNull(command);
    }
}
