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

class RotateHandlerTest {

    private RotateHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RotateHandler(AppState.getInstance());
    }

    @Test
    void calculateAngleReturnsAngleFromCenterToPoint() {
        double angle = handler.calculateAngle(new Vec2d(0, 0), new Vec2d(10, 0));

        assertEquals(0.0, angle, 1e-6);
        assertEquals(Math.PI / 2, handler.calculateAngle(new Vec2d(0, 0), new Vec2d(0, 10)), 1e-6);
    }

    @Test
    void calculateAngleDifferenceWrapsToShortestArc() {
        assertEquals(Math.PI / 2, handler.calculateAngleDifference(0.0, Math.PI / 2), 1e-6);
        assertEquals(-Math.PI / 2, handler.calculateAngleDifference(0.0, 3 * Math.PI / 2), 1e-6);
        assertEquals(0.0, handler.calculateAngleDifference(Math.PI, Math.PI), 1e-6);
    }

    @Test
    void validateModificationRequiresCenterAndAngle() {
        IModifyHandler.ValidationResult missingParams = handler.validateModification(
                List.of(new LineShape(new Vec2d(0, 0), new Vec2d(10, 0))),
                new ModifyParameters());
        assertFalse(missingParams.isValid());

        ModifyParameters tinyAngle = rotateParams(new Vec2d(0, 0), 0.0001);
        IModifyHandler.ValidationResult tinyAngleResult = handler.validateModification(
                List.of(new LineShape(new Vec2d(0, 0), new Vec2d(10, 0))),
                tinyAngle);
        assertFalse(tinyAngleResult.isValid());
    }

    @Test
    void calculateModifiedShapesRotatesAroundCenter() {
        LineShape line = new LineShape(new Vec2d(10, 0), new Vec2d(10, 10));
        ModifyParameters params = rotateParams(new Vec2d(0, 0), Math.PI / 2);

        List<Shape> result = handler.calculateModifiedShapes(List.of(line), params);

        LineShape rotated = (LineShape) result.getFirst();
        assertEquals(0.0, rotated.getStart().x, 1e-6);
        assertEquals(10.0, rotated.getStart().y, 1e-6);
        assertEquals(-10.0, rotated.getEnd().x, 1e-6);
        assertEquals(10.0, rotated.getEnd().y, 1e-6);
    }

    @Test
    void calculateCenterPointAveragesShapePositions() {
        Vec2d center = handler.calculateCenterPoint(List.of(
                new LineShape(new Vec2d(0, 0), new Vec2d(10, 0)),
                new LineShape(new Vec2d(10, 0), new Vec2d(10, 10))));

        assertEquals(5.0, center.x, 1e-6);
        assertTrue(center.y >= 0.0);
    }

    private static ModifyParameters rotateParams(Vec2d center, double angleRadians) {
        ModifyParameters params = new ModifyParameters();
        params.setCenterPoint(center);
        params.setDouble(ModifyParameters.ROTATION_ANGLE, angleRadians);
        return params;
    }
}
