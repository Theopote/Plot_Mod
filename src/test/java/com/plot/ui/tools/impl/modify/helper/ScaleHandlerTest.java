package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.dto.ModifyParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ScaleHandlerTest {

    private ScaleHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ScaleHandler(AppState.getInstance());
    }

    @Test
    void validateModificationRequiresCenterAndValidFactor() {
        IModifyHandler.ValidationResult missingCenter = handler.validateModification(
                List.of(new LineShape(new Vec2d(0, 0), new Vec2d(10, 0))),
                new ModifyParameters());
        assertFalse(missingCenter.isValid());

        ModifyParameters outOfRange = uniformScaleParams(new Vec2d(0, 0), 0.001);
        IModifyHandler.ValidationResult invalidFactor = handler.validateModification(
                List.of(new LineShape(new Vec2d(0, 0), new Vec2d(10, 0))),
                outOfRange);
        assertFalse(invalidFactor.isValid());
    }

    @Test
    void calculateModifiedShapesScalesUniformlyFromCenter() {
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        ModifyParameters params = uniformScaleParams(new Vec2d(0, 0), 2.0);

        List<Shape> result = handler.calculateModifiedShapes(List.of(line), params);

        LineShape scaled = (LineShape) result.getFirst();
        assertEquals(0.0, scaled.getStart().x, 1e-6);
        assertEquals(0.0, scaled.getStart().y, 1e-6);
        assertEquals(20.0, scaled.getEnd().x, 1e-6);
        assertEquals(0.0, scaled.getEnd().y, 1e-6);
    }

    @Test
    void calculateModifiedShapesScalesNonUniformly() {
        CircleShape circle = new CircleShape(new Vec2d(10, 10), 5);
        ModifyParameters params = nonUniformScaleParams(new Vec2d(0, 0), 2.0, 0.5);

        List<Shape> result = handler.calculateModifiedShapes(List.of(circle), params);

        CircleShape scaled = (CircleShape) result.getFirst();
        assertEquals(20.0, scaled.getCenter().x, 1e-6);
        assertEquals(5.0, scaled.getCenter().y, 1e-6);
    }

    @Test
    void calculateModifiedShapesUsesShapeCenterMode() {
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        ModifyParameters params = uniformScaleParams(new Vec2d(100, 100), 2.0);
        params.setParameter("scaleCenterMode", "shape");

        List<Shape> result = handler.calculateModifiedShapes(List.of(line), params);

        LineShape scaled = (LineShape) result.getFirst();
        assertEquals(-5.0, scaled.getStart().x, 1e-6);
        assertEquals(15.0, scaled.getEnd().x, 1e-6);
    }

    private static ModifyParameters uniformScaleParams(Vec2d center, double factor) {
        ModifyParameters params = new ModifyParameters();
        params.setCenterPoint(center);
        params.setUniformScale(true);
        params.setScaleFactor(factor);
        return params;
    }

    private static ModifyParameters nonUniformScaleParams(Vec2d center, double scaleX, double scaleY) {
        ModifyParameters params = new ModifyParameters();
        params.setCenterPoint(center);
        params.setUniformScale(false);
        params.setDouble(ModifyParameters.SCALE_X, scaleX);
        params.setDouble(ModifyParameters.SCALE_Y, scaleY);
        return params;
    }
}
