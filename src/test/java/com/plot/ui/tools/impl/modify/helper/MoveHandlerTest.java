package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.constants.ModifyConstraints;
import com.plot.ui.tools.impl.modify.dto.ModifyParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MoveHandlerTest {

    private MoveHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MoveHandler(AppState.getInstance());
    }

    @Test
    void validateModificationRequiresDistinctStartAndEnd() {
        IModifyHandler.ValidationResult missingPoints = handler.validateModification(
                List.of(new LineShape(new Vec2d(0, 0), new Vec2d(10, 0))),
                new ModifyParameters());
        assertFalse(missingPoints.isValid());

        ModifyParameters noMovement = moveParams(new Vec2d(0, 0), new Vec2d(0, 0));
        IModifyHandler.ValidationResult zeroMove = handler.validateModification(
                List.of(new LineShape(new Vec2d(0, 0), new Vec2d(10, 0))),
                noMovement);
        assertFalse(zeroMove.isValid());
    }

    @Test
    void calculateModifiedShapesTranslatesGeometry() {
        CircleShape circle = new CircleShape(new Vec2d(0, 0), 5);
        ModifyParameters params = moveParams(new Vec2d(0, 0), new Vec2d(12, 3));

        List<Shape> result = handler.calculateModifiedShapes(List.of(circle), params);

        CircleShape moved = (CircleShape) result.getFirst();
        assertEquals(12.0, moved.getCenter().x, 1e-6);
        assertEquals(3.0, moved.getCenter().y, 1e-6);
    }

    @Test
    void calculateMoveVectorAndAngle() {
        ModifyParameters params = moveParams(new Vec2d(0, 0), new Vec2d(10, 10));

        Vec2d vector = handler.calculateMoveVector(params);

        assertEquals(10.0, vector.x, 1e-6);
        assertEquals(10.0, vector.y, 1e-6);
        assertEquals(Math.PI / 4, handler.calculateMoveAngle(params), 1e-6);
    }

    @Test
    void applyConstraintsForcesOrthogonalMovement() {
        ModifyParameters params = moveParams(new Vec2d(0, 0), new Vec2d(10, 3));
        ModifyConstraints constraints = new ModifyConstraints();
        constraints.setOrthogonalConstraintEnabled(true);

        ModifyParameters constrained = (ModifyParameters) handler.applyConstraints(params, constraints);

        assertEquals(10.0, constrained.getEndPoint().x, 1e-6);
        assertEquals(0.0, constrained.getEndPoint().y, 1e-6);
    }

    private static ModifyParameters moveParams(Vec2d start, Vec2d end) {
        ModifyParameters params = new ModifyParameters();
        params.setStartPoint(start);
        params.setEndPoint(end);
        return params;
    }
}
