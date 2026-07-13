package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.ArcShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.constants.FilletConstants;
import com.plot.ui.tools.impl.modify.dto.ModifyParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilletHandlerTest {

    private FilletHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FilletHandler(AppState.getInstance());
    }

    @Test
    void validateModificationRejectsWrongShapeCount() {
        ModifyParameters params = filletParams(5.0, new Vec2d(5, 0), new Vec2d(10, 5));

        IModifyHandler.ValidationResult result = handler.validateModification(
                List.of(new LineShape(new Vec2d(0, 0), new Vec2d(10, 0))),
                params);

        assertFalse(result.isValid());
    }

    @Test
    void validateModificationAcceptsPerpendicularLines() {
        LineShape horizontal = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        LineShape vertical = new LineShape(new Vec2d(10, 0), new Vec2d(10, 10));
        ModifyParameters params = filletParams(2.0, new Vec2d(5, 0), new Vec2d(10, 5));

        IModifyHandler.ValidationResult result = handler.validateModification(
                List.of(horizontal, vertical),
                params);

        assertTrue(result.isValid());
    }

    @Test
    void calculateModifiedShapesCreatesArcForCornerFillet() {
        LineShape horizontal = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        LineShape vertical = new LineShape(new Vec2d(10, 0), new Vec2d(10, 10));
        ModifyParameters params = filletParams(2.0, new Vec2d(5, 0), new Vec2d(10, 5));

        List<Shape> result = handler.calculateModifiedShapes(List.of(horizontal, vertical), params);

        assertTrue(result.size() >= 2);
        assertTrue(result.stream().anyMatch(shape -> shape instanceof ArcShape),
                "fillet should introduce a connecting arc");
    }

    @Test
    void validateModificationRejectsRadiusOutsideRange() {
        LineShape horizontal = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        LineShape vertical = new LineShape(new Vec2d(10, 0), new Vec2d(10, 10));
        ModifyParameters params = filletParams(0.1, new Vec2d(5, 0), new Vec2d(10, 5));

        IModifyHandler.ValidationResult result = handler.validateModification(
                List.of(horizontal, vertical),
                params);

        assertFalse(result.isValid());
    }

    private static ModifyParameters filletParams(double radius, Vec2d click1, Vec2d click2) {
        ModifyParameters params = new ModifyParameters();
        params.setParameter(FilletConstants.CONFIG_KEY_RADIUS, radius);
        params.setParameter("clickPoint1", click1);
        params.setParameter("clickPoint2", click2);
        return params;
    }
}
