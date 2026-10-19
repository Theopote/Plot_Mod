package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.strategy.ExtendWithSelectionStrategy.ExtendState;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy.ModifyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import com.plot.core.model.Shape;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtendWithSelectionStrategyTest {

    private static final int MOUSE_RIGHT = 1;
    private static final int ESC_KEY = 27;

    private AppState appState;
    private ExtendWithSelectionStrategy strategy;
    private ModifyStrategyTestContext context;
    private LineShape boundaryLine;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        strategy = new ExtendWithSelectionStrategy(appState);
        context = new ModifyStrategyTestContext(appState);
        boundaryLine = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        appState.addShape(boundaryLine);
    }

    @Test
    void prepareForNextExtendRetainsBoundaryShapes() throws Exception {
        injectExtendModeWithBoundary(boundaryLine);

        strategy.prepareForNextExtend(context);

        assertEquals(ExtendState.EXTENDING, strategy.getExtendState());
        assertEquals(1, strategy.getBoundaryShapes().size());
        assertEquals(boundaryLine.getId(), strategy.getBoundaryShapes().getFirst().getId());
    }

    @Test
    void finishExtendSessionClearsBoundaryAndReturnsToSelection() throws Exception {
        injectExtendModeWithBoundary(boundaryLine);

        strategy.finishExtendSession(context);

        assertEquals(ExtendState.SELECTING_BOUNDARY, strategy.getExtendState());
        assertTrue(strategy.getBoundaryShapes().isEmpty());
        assertTrue(context.getSelectedShapes().isEmpty());
    }

    @Test
    void rightClickDuringExtendEndsContinuousSession() throws Exception {
        injectExtendModeWithBoundary(boundaryLine);

        ModifyResult result = strategy.onMouseDown(new Vec2d(5, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals(ExtendState.SELECTING_BOUNDARY, strategy.getExtendState());
        assertTrue(strategy.getBoundaryShapes().isEmpty());
    }

    @Test
    void escapeDuringExtendEndsContinuousSession() throws Exception {
        injectExtendModeWithBoundary(boundaryLine);

        ModifyResult result = strategy.onKeyDown(ESC_KEY, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals(ExtendState.SELECTING_BOUNDARY, strategy.getExtendState());
        assertTrue(strategy.getBoundaryShapes().isEmpty());
    }

    private void injectExtendModeWithBoundary(LineShape boundary) throws Exception {
        Field extendStateField = ExtendWithSelectionStrategy.class.getDeclaredField("extendState");
        extendStateField.setAccessible(true);
        extendStateField.set(strategy, ExtendState.EXTENDING);

        Field boundaryField = ExtendWithSelectionStrategy.class.getDeclaredField("boundaryShapes");
        boundaryField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Shape> boundaryShapes = (List<Shape>) boundaryField.get(strategy);
        boundaryShapes.clear();
        boundaryShapes.add(boundary);
    }
}
