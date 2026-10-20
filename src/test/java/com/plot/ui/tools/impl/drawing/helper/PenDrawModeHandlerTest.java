package com.plot.ui.tools.impl.drawing.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.BezierCurveShape;
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

class PenDrawModeHandlerTest {

    private static final int MOUSE_LEFT = 0;
    private static final int MOUSE_RIGHT = 1;
    private static final int ESC_KEY = 27;

    private PenDrawModeHandler handler;
    private DrawingToolTestContext.ModeHandlerContext context;

    @BeforeEach
    void setUp() {
        AppState appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        StyleHandler styleHandler = new StyleHandler(appState, "pen-test");
        handler = new PenDrawModeHandler(styleHandler::getFinalStyle);
        context = DrawingToolTestContext.forModeHandler(appState);
    }

    @Test
    void firstClickStartsDrawing() {
        handler.onMouseDown(new Vec2d(0, 0), MOUSE_LEFT, context);

        assertTrue(handler.isDrawing());
        assertEquals(PenDrawingState.WAITING_FOR_POINT, handler.getState());
    }

    @Test
    void rightClickWithInsufficientPointsCancelsDrawing() {
        handler.onMouseDown(new Vec2d(0, 0), MOUSE_LEFT, context);

        InteractionResult result = handler.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(InteractionResult.CANCEL, result);
        assertFalse(handler.isDrawing());
        assertEquals(PenDrawingState.IDLE, handler.getState());
        assertEquals(null, handler.getFinalShape());
    }

    @Test
    void rightClickCompletesInjectedStraightPath() throws Exception {
        injectPathNodes(
                new PathNode(new Vec2d(0, 0)),
                new PathNode(new Vec2d(10, 0)));

        InteractionResult result = handler.onMouseDown(new Vec2d(10, 0), MOUSE_RIGHT, context);

        assertEquals(InteractionResult.COMPLETE, result);
        Shape finalShape = handler.getFinalShape();
        assertNotNull(finalShape);
        assertInstanceOf(BezierCurveShape.class, finalShape);
        assertEquals(2, ((BezierCurveShape) finalShape).getAnchorPoints().size());
    }

    @Test
    void escapeCancelsDrawing() throws Exception {
        injectPathNodes(
                new PathNode(new Vec2d(0, 0)),
                new PathNode(new Vec2d(10, 0)));

        InteractionResult result = handler.onKeyDown(ESC_KEY, context);

        assertEquals(InteractionResult.CANCEL, result);
        assertFalse(handler.isDrawing());
        assertEquals(PenDrawingState.IDLE, handler.getState());
    }

    @Test
    void getFinalShapeRequiresAtLeastTwoNodes() throws Exception {
        injectPathNodes(new PathNode(new Vec2d(0, 0)));

        assertEquals(null, handler.getFinalShape());
    }

    @Test
    void rightClickWithCloseEnabledProducesClosedShape() throws Exception {
        context.setCloseEnabled(true);
        injectPathNodes(
                new PathNode(new Vec2d(0, 0)),
                new PathNode(new Vec2d(10, 0)),
                new PathNode(new Vec2d(10, 10)));

        InteractionResult result = handler.onMouseDown(new Vec2d(10, 10), MOUSE_RIGHT, context);

        assertEquals(InteractionResult.COMPLETE, result);
        Shape finalShape = handler.getFinalShape();
        assertNotNull(finalShape);
        if (finalShape instanceof PolylineShape polyline) {
            assertTrue(polyline.isClosed());
        } else {
            assertInstanceOf(BezierCurveShape.class, finalShape);
            assertTrue(((BezierCurveShape) finalShape).isClosed());
        }
    }

    @Test
    void clickStartAnchorWithCloseEnabledCompletesAndCloses() throws Exception {
        context.setCloseEnabled(true);
        injectPathNodes(
                new PathNode(new Vec2d(0, 0)),
                new PathNode(new Vec2d(10, 0)),
                new PathNode(new Vec2d(10, 10)));

        InteractionResult result = handler.onMouseDown(new Vec2d(0, 0), MOUSE_LEFT, context);

        assertEquals(InteractionResult.COMPLETE, result);
        Shape finalShape = handler.getFinalShape();
        assertNotNull(finalShape);
        if (finalShape instanceof PolylineShape polyline) {
            assertTrue(polyline.isClosed());
            assertEquals(3, polyline.getPoints().size());
        } else {
            assertInstanceOf(BezierCurveShape.class, finalShape);
            BezierCurveShape curve = (BezierCurveShape) finalShape;
            assertTrue(curve.isClosed());
            // 闭合曲线 getAnchorPoints 末尾会重复起点，去重后应为 3
            assertEquals(3, curve.getAnchorPoints().stream().distinct().count());
        }
    }

    private void injectPathNodes(PathNode... nodes) throws Exception {
        Field nodesField = PenDrawModeHandler.class.getDeclaredField("pathNodes");
        nodesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<PathNode> nodeList = (List<PathNode>) nodesField.get(handler);
        nodeList.clear();
        nodeList.addAll(List.of(nodes));

        Field stateField = PenDrawModeHandler.class.getDeclaredField("state");
        stateField.setAccessible(true);
        stateField.set(handler, PenDrawingState.WAITING_FOR_POINT);

        Field isDrawingField = AbstractModeHandler.class.getDeclaredField("isDrawing");
        isDrawingField.setAccessible(true);
        isDrawingField.set(handler, true);
    }
}
