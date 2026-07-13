package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.model.Shape;
import com.plot.ui.tools.impl.modify.constants.AlignConstants;
import com.plot.ui.tools.impl.modify.dto.ModifyParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlignHandlerTest {

    private AlignHandler handler;

    @BeforeEach
    void setUp() {
        handler = AlignHandler.getInstance();
    }

    @Test
    void validateModificationRequiresMinimumSelection() {
        ModifyParameters params = alignParams("LEFT", "SELECTION_BOUNDS");

        IModifyHandler.ValidationResult result = handler.validateModification(
                List.of(new CircleShape(new Vec2d(0, 0), 5)),
                params);

        assertFalse(result.isValid());
    }

    @Test
    void calculateModifiedShapesLeftAlignsToSelectionBounds() {
        CircleShape left = new CircleShape(new Vec2d(0, 0), 5);
        CircleShape right = new CircleShape(new Vec2d(30, 0), 5);
        ModifyParameters params = alignParams("LEFT", "SELECTION_BOUNDS");

        List<Shape> result = handler.calculateModifiedShapes(List.of(left, right), params);

        CircleShape alignedRight = (CircleShape) result.get(1);
        assertEquals(0.0, alignedRight.getCenter().x, 1e-6);
        assertEquals(0.0, alignedRight.getCenter().y, 1e-6);
    }

    @Test
    void calculateModifiedShapesCentersHorizontallyWithinSelectionBounds() {
        CircleShape left = new CircleShape(new Vec2d(0, 0), 5);
        CircleShape right = new CircleShape(new Vec2d(40, 0), 5);
        ModifyParameters params = alignParams("CENTER", "SELECTION_BOUNDS");

        List<Shape> result = handler.calculateModifiedShapes(List.of(left, right), params);

        CircleShape alignedRight = (CircleShape) result.get(1);
        assertEquals(20.0, alignedRight.getCenter().x, 1e-6);
    }

    @Test
    void validateModificationRequiresDistributeMinimumSelection() {
        ModifyParameters params = alignParams("DISTRIBUTE_H", "SELECTION_BOUNDS");

        IModifyHandler.ValidationResult result = handler.validateModification(
                List.of(
                        new CircleShape(new Vec2d(0, 0), 5),
                        new CircleShape(new Vec2d(20, 0), 5)),
                params);

        assertFalse(result.isValid());
        assertTrue(handler.validateModification(
                List.of(
                        new CircleShape(new Vec2d(0, 0), 5),
                        new CircleShape(new Vec2d(20, 0), 5),
                        new CircleShape(new Vec2d(40, 0), 5)),
                params).isValid());
    }

    private static ModifyParameters alignParams(String alignMode, String referenceMode) {
        ModifyParameters params = new ModifyParameters();
        params.setParameter(AlignConstants.ALIGN_MODE, alignMode);
        params.setParameter(AlignConstants.REFERENCE_MODE, referenceMode);
        return params;
    }
}
