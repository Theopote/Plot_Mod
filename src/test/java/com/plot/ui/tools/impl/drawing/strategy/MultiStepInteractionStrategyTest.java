package com.plot.ui.tools.impl.drawing.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.ui.tools.impl.drawing.DrawingTool;
import com.plot.ui.tools.impl.drawing.helper.DrawingToolTestContext;
import com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy.InteractionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiStepInteractionStrategyTest {

    private static final int MOUSE_LEFT = 0;

    private MultiStepInteractionStrategy strategy;
    private DrawingToolTestContext context;

    @BeforeEach
    void setUp() {
        strategy = MultiStepInteractionStrategy.forLineTool();
        context = DrawingToolTestContext.lineToolContext();
    }

    @Test
    void lineToolCompletesAfterTwoClicks() {
        assertEquals(InteractionResult.CONTINUE, strategy.onMouseDown(new Vec2d(0, 0), MOUSE_LEFT, context));
        assertTrue(strategy.isActive());
        assertEquals(1, strategy.getControlPoints().size());
        assertEquals(DrawingTool.ToolState.DRAWING, context.getToolState());

        assertEquals(InteractionResult.COMPLETE, strategy.onMouseDown(new Vec2d(10, 0), MOUSE_LEFT, context));

        Shape finalShape = strategy.getFinalShape();
        assertNotNull(finalShape);
        assertInstanceOf(LineShape.class, finalShape);
        LineShape line = (LineShape) finalShape;
        assertEquals(0.0, line.getStart().x, 1e-6);
        assertEquals(10.0, line.getEnd().x, 1e-6);
    }

    @Test
    void resetClearsControlPoints() {
        strategy.onMouseDown(new Vec2d(0, 0), MOUSE_LEFT, context);
        strategy.reset();

        assertFalse(strategy.isActive());
        assertTrue(strategy.getControlPoints().isEmpty());
        assertEquals(null, strategy.getFinalShape());
    }

    @Test
    void mouseMoveUpdatesPreviewShape() {
        strategy.onMouseDown(new Vec2d(0, 0), MOUSE_LEFT, context);
        strategy.onMouseMove(new Vec2d(10, 5), context);

        assertNotNull(context.getPreviewShape());
        assertInstanceOf(LineShape.class, context.getPreviewShape());
    }
}
