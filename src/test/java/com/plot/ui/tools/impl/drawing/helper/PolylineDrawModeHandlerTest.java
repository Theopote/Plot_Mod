package com.plot.ui.tools.impl.drawing.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy.InteractionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolylineDrawModeHandlerTest {

    private static final int MOUSE_LEFT = 0;
    private static final int MOUSE_RIGHT = 1;
    private static final int ESC_KEY = 27;

    private AppState appState;
    private PolylineDrawModeHandler handler;
    private DrawingToolTestContext.ModeHandlerContext context;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        StyleHandler styleHandler = new StyleHandler(appState, "polyline-test");
        handler = new PolylineDrawModeHandler(styleHandler::getFinalStyle);
        context = DrawingToolTestContext.forModeHandler(appState);
    }

    @Test
    void firstClickStartsDrawing() {
        handler.onMouseDown(new Vec2d(0, 0), MOUSE_LEFT, context);

        assertTrue(handler.isDrawing());
        assertEquals(1, handler.getPoints().size());
    }

    @Test
    void rightClickCompletesInjectedPolyline() throws Exception {
        injectDrawingPoints(
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                new Vec2d(10, 10));

        InteractionResult result = handler.onMouseDown(new Vec2d(10, 10), MOUSE_RIGHT, context);

        assertEquals(InteractionResult.COMPLETE, result);
        Shape finalShape = handler.getFinalShape();
        assertNotNull(finalShape);
        assertInstanceOf(PolylineShape.class, finalShape);
        assertEquals(3, ((PolylineShape) finalShape).getPoints().size());
    }

    @Test
    void rightClickWithInsufficientPointsCancelsDrawing() {
        handler.onMouseDown(new Vec2d(0, 0), MOUSE_LEFT, context);

        InteractionResult result = handler.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(InteractionResult.CANCEL, result);
        assertFalse(handler.isDrawing());
        assertEquals(null, handler.getFinalShape());
    }

    @Test
    void escapeCancelsDrawing() throws Exception {
        injectDrawingPoints(new Vec2d(0, 0), new Vec2d(10, 0));

        InteractionResult result = handler.onKeyDown(ESC_KEY, context);

        assertEquals(InteractionResult.CANCEL, result);
        assertFalse(handler.isDrawing());
        assertTrue(handler.getPoints().isEmpty());
    }

    @Test
    void getFinalShapeRequiresAtLeastTwoPoints() throws Exception {
        injectDrawingPoints(new Vec2d(0, 0));

        assertEquals(null, handler.getFinalShape());
    }

    private void injectDrawingPoints(Vec2d... points) throws Exception {
        Field pointsField = PolylineDrawModeHandler.class.getDeclaredField("points");
        pointsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Vec2d> pointList = (List<Vec2d>) pointsField.get(handler);
        pointList.clear();
        pointList.addAll(List.of(points));

        Field isDrawingField = AbstractModeHandler.class.getDeclaredField("isDrawing");
        isDrawingField.setAccessible(true);
        isDrawingField.set(handler, true);
    }
}
