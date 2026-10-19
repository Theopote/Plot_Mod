package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.strategy.TrimWithSelectionStrategy.TrimState;
import com.plot.ui.tools.impl.modify.strategy.TrimWithSelectionStrategy.TrimType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrimWithSelectionStrategyTest {

    private AppState appState;
    private TrimWithSelectionStrategy strategy;
    private ModifyStrategyTestContext context;
    private LineShape boundaryLine;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        strategy = new TrimWithSelectionStrategy();
        strategy.setTrimType(TrimType.BOUNDARY);
        context = new ModifyStrategyTestContext(appState);
        boundaryLine = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
    }

    @Test
    void continuousModeEnabledByDefault() throws Exception {
        assertTrue(readContinuousMode(strategy));
    }

    @Test
    void onTrimCommandExecutedRetainsBoundaryInContinuousMode() throws Exception {
        injectBoundary(boundaryLine);
        setTrimState(TrimState.WAITING_TRIM_CLICK);

        strategy.onTrimCommandExecuted(context);

        assertEquals(TrimState.BOUNDARY_READY, strategy.getTrimState());
        assertEquals(1, strategy.getSelectedCount());
        assertEquals("status.plot.trim.complete_continuous", context.getLastStatusMessage());
    }

    @Test
    void onTrimCommandExecutedReturnsToWaitingClickWhenContinuousModeDisabled() throws Exception {
        strategy.setContinuousMode(false);
        injectBoundary(boundaryLine);
        setTrimState(TrimState.WAITING_TRIM_CLICK);

        strategy.onTrimCommandExecuted(context);

        assertEquals(TrimState.WAITING_TRIM_CLICK, strategy.getTrimState());
        assertEquals(1, strategy.getSelectedCount());
    }

    @SuppressWarnings("unchecked")
    private void injectBoundary(Shape boundary) throws Exception {
        Field field = TrimWithSelectionStrategy.class.getDeclaredField("boundaryShapes");
        field.setAccessible(true);
        List<Shape> boundaryShapes = (List<Shape>) field.get(strategy);
        boundaryShapes.clear();
        boundaryShapes.add(boundary);
    }

    private void setTrimState(TrimState state) throws Exception {
        Field field = TrimWithSelectionStrategy.class.getDeclaredField("trimState");
        field.setAccessible(true);
        field.set(strategy, state);
    }

    private static boolean readContinuousMode(TrimWithSelectionStrategy strategy) throws Exception {
        Field field = TrimWithSelectionStrategy.class.getDeclaredField("continuousMode");
        field.setAccessible(true);
        return field.getBoolean(strategy);
    }
}
