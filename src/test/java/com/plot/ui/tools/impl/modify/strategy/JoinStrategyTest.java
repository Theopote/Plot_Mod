package com.plot.ui.tools.impl.modify.strategy;

import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinStrategyTest {

    private AppState appState;
    private JoinStrategy strategy;
    private ModifyStrategyTestContext context;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        strategy = new JoinStrategy();
        context = new ModifyStrategyTestContext(appState);
    }

    @Test
    void prepareFailsWhenNothingSelected() {
        assertFalse(strategy.prepare(context));
        assertEquals("status.plot.join.select_first", context.getLastStatusMessage());
    }

    @Test
    void prepareJoinsConnectedLinesIntoPolyline() {
        LineShape line1 = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        LineShape line2 = new LineShape(new Vec2d(10, 0), new Vec2d(20, 0));
        LineShape line3 = new LineShape(new Vec2d(20, 0), new Vec2d(30, 0));
        context.setSelectedShapes(List.of(line1, line2, line3));

        assertTrue(strategy.prepare(context));

        ModifyCommand command = strategy.getModifyCommand();
        assertNotNull(command);
        assertEquals(1, command.getNewShapes().size());
        assertInstanceOf(PolylineShape.class, command.getNewShapes().getFirst());
    }

    @Test
    void prepareReportsNoEffectForIsolatedLine() {
        LineShape isolated = new LineShape(new Vec2d(0, 10), new Vec2d(5, 10));
        context.setSelectedShapes(List.of(isolated));

        assertFalse(strategy.prepare(context));
        assertEquals("status.plot.join.nothing", context.getLastStatusMessage());
    }
}
