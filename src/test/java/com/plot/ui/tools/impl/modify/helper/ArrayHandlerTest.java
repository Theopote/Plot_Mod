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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArrayHandlerTest {

    private ArrayHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ArrayHandler(AppState.getInstance());
    }

    @Test
    void validateModificationRequiresArrayTypeAndBasePoint() {
        IModifyHandler.ValidationResult missingType = handler.validateModification(
                List.of(new LineShape(new Vec2d(0, 0), new Vec2d(10, 0))),
                new ModifyParameters());
        assertFalse(missingType.isValid());

        ModifyParameters missingBase = new ModifyParameters();
        missingBase.setParameter("arrayType", "RECTANGULAR");
        IModifyHandler.ValidationResult missingBaseResult = handler.validateModification(
                List.of(new LineShape(new Vec2d(0, 0), new Vec2d(10, 0))),
                missingBase);
        assertFalse(missingBaseResult.isValid());
    }

    @Test
    void calculateRectangularArraySkipsOriginalPosition() {
        CircleShape source = new CircleShape(new Vec2d(0, 0), 5);
        ModifyParameters params = rectangularParams(2, 2, 20, 10, new Vec2d(0, 0));

        List<Shape> result = handler.calculateModifiedShapes(List.of(source), params);

        assertEquals(3, result.size());
    }

    @Test
    void calculateRectangularArrayTranslatesCopiesBySpacing() {
        CircleShape source = new CircleShape(new Vec2d(0, 0), 5);
        ModifyParameters params = rectangularParams(1, 2, 30, 0, new Vec2d(0, 0));

        List<Shape> result = handler.calculateModifiedShapes(List.of(source), params);

        assertEquals(1, result.size());
        CircleShape copy = (CircleShape) result.getFirst();
        assertEquals(30.0, copy.getCenter().x, 1e-6);
        assertEquals(0.0, copy.getCenter().y, 1e-6);
    }

    @Test
    void calculateCircularArrayPlacesCopiesOnOrbit() {
        CircleShape source = new CircleShape(new Vec2d(100, 0), 5);
        ModifyParameters params = circularParams(4, 100, new Vec2d(0, 0));

        List<Shape> result = handler.calculateModifiedShapes(List.of(source), params);

        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(shape -> {
            Vec2d center = ((CircleShape) shape).getCenter();
            return Math.abs(center.x) < 1e-3 && Math.abs(center.y - 100.0) < 1e-3;
        }));
        assertTrue(result.stream().anyMatch(shape -> {
            Vec2d center = ((CircleShape) shape).getCenter();
            return Math.abs(center.x + 100.0) < 1e-3 && Math.abs(center.y) < 1e-3;
        }));
    }

    private static ModifyParameters rectangularParams(
            int rows, int cols, double columnSpacing, double rowSpacing, Vec2d basePoint) {
        ModifyParameters params = new ModifyParameters();
        params.setParameter("arrayType", "RECTANGULAR");
        params.setInt("rowCount", rows);
        params.setInt("columnCount", cols);
        params.setDouble("columnSpacing", columnSpacing);
        params.setDouble("rowSpacing", rowSpacing);
        params.setVec2d("basePoint", basePoint);
        return params;
    }

    private static ModifyParameters circularParams(int count, double radius, Vec2d basePoint) {
        ModifyParameters params = new ModifyParameters();
        params.setParameter("arrayType", "CIRCULAR");
        params.setInt("rowCount", count);
        params.setDouble("radius", radius);
        params.setVec2d("basePoint", basePoint);
        return params;
    }
}
